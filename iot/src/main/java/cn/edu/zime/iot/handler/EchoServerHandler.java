package cn.edu.zime.iot.handler;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

public class EchoServerHandler extends ChannelInboundHandlerAdapter {
    // 存储 clientId -> ctx 的映射
    private static final ConcurrentHashMap<String, ChannelHandlerContext> allClients = new ConcurrentHashMap<>();
    // 存储 ctx -> clientId 的映射（方便反查）
    private static final ConcurrentHashMap<ChannelHandlerContext, String> ctxToClientId = new ConcurrentHashMap<>();

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String received = (String) msg;
        System.out.println("服务器收到: " + received);

        String[] parts = received.split("\\|", 3);
        String cmd = parts[0];

        switch (cmd) {
            case "reg":
                if (parts.length < 2) {
                    ctx.writeAndFlush("ERR|参数错误，格式: reg|clientID");
                    return;
                }
                String clientId = parts[1];
                allClients.put(clientId, ctx);
                ctxToClientId.put(ctx, clientId);
                ctx.writeAndFlush("REG|OK");
                broadcastClientList();
                break;

            case "unreg":
                if (parts.length < 2) {
                    ctx.writeAndFlush("ERR|参数错误，格式: unreg|clientID");
                    return;
                }
                String unregId = parts[1];
                allClients.remove(unregId);
                ctxToClientId.remove(ctx);
                ctx.writeAndFlush("UNREG|OK");
                broadcastClientList();
                break;

            case "list":
                sendClientList(ctx);
                break;

            case "broadcast":
                if (parts.length < 2) {
                    ctx.writeAndFlush("ERR|参数错误，格式: broadcast|消息内容");
                    return;
                }
                String broadcastMsg = parts[1];
                String fromClient = ctxToClientId.get(ctx);
                if (fromClient == null) {
                    ctx.writeAndFlush("ERR|未登记，不能发送广播");
                    return;
                }
                broadcastMessage("BROADCAST|" + fromClient + "|" + broadcastMsg);
                break;

            case "chat":
                if (parts.length < 3) {
                    ctx.writeAndFlush("ERR|参数错误，格式: chat|好友ID|消息内容");
                    return;
                }
                String targetId = parts[1];
                String chatMsg = parts[2];
                ChannelHandlerContext targetCtx = allClients.get(targetId);
                String senderId = ctxToClientId.get(ctx);
                if (senderId == null) {
                    ctx.writeAndFlush("ERR|未登记，不能私聊");
                    return;
                }
                if (targetCtx != null && targetCtx.channel().isActive()) {
                    targetCtx.writeAndFlush("CHAT|" + senderId + "|" + chatMsg);
                    ctx.writeAndFlush("CHAT|TO|" + targetId + "|发送成功");
                } else {
                    ctx.writeAndFlush("ERR|好友" + targetId + "不在线");
                }
                break;

            default:
                ctx.writeAndFlush("ERR|未知命令");
                break;
        }
    }

    private void sendClientList(ChannelHandlerContext ctx) {
        Set<String> clients = allClients.keySet();
        String listStr = String.join(",", clients);
        ctx.writeAndFlush("LIST|" + listStr);
    }

    private void broadcastClientList() {
        Set<String> clients = allClients.keySet();
        String listStr = String.join(",", clients);
        for (ChannelHandlerContext ctx : allClients.values()) {
            if (ctx.channel().isActive()) {
                ctx.writeAndFlush("LIST|" + listStr);
            }
        }
    }

    private void broadcastMessage(String msg) {
        for (ChannelHandlerContext ctx : allClients.values()) {
            if (ctx.channel().isActive()) {
                ctx.writeAndFlush(msg);
            }
        }
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        String clientId = ctxToClientId.remove(ctx);
        if (clientId != null) {
            allClients.remove(clientId);
            System.out.println("客户端断开连接: " + clientId);
            broadcastClientList();
        }
        super.channelInactive(ctx);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

