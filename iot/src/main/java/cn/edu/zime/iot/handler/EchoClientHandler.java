package cn.edu.zime.iot.handler;

import cn.edu.zime.iot.client.EchoClient;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.channel.SimpleChannelInboundHandler;

public class EchoClientHandler extends ChannelInboundHandlerAdapter {

    private final EchoClient client;

    public EchoClientHandler(EchoClient client) {
        this.client = client;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        String received = (String) msg;

        // 简单解析协议，打印消息
        if (received.startsWith("REG|OK")) {
            System.out.println("[系统] " + client.getClientId() + " 注册成功");
        } else if (received.startsWith("UNREG|OK")) {
            System.out.println("[系统] " + client.getClientId() + " 注销成功");
        } else if (received.startsWith("LIST|")) {
            String clients = received.substring(5);
            client.handleClientList(clients);
        } else if (received.startsWith("BROADCAST|")) {
            String[] parts = received.split("\\|", 3);
            if(parts.length == 3) {
                System.out.println("[广播][" + parts[1] + "]: " + parts[2]);
            }
        } else if (received.startsWith("CHAT|")) {
            String[] parts = received.split("\\|", 4);
            if (parts.length == 4 && "TO".equals(parts[1])) {
                // 发送者收到的发送确认
                System.out.println("[私聊确认] 给 " + parts[2] + ": " + parts[3]);
            } else if (parts.length == 3) {
                System.out.println("[私聊][" + parts[1] + "]: " + parts[2]);
            }
        } else if (received.startsWith("ERR|")) {
            System.err.println("[错误] " + received.substring(4));
        } else if (received.startsWith("OFFLINE|")) {
            // 服务器主动推送的离线通知
            String offlineClient = received.substring(8);
            System.out.println("[系统提醒] " + offlineClient + " 已离线");
        } else if (received.startsWith("ONLINE|")) {
            // 服务器主动推送的上线通知
            String onlineClient = received.substring(7);
            System.out.println("[系统提醒] " + onlineClient + " 已上线");
        } else {
            System.out.println("[消息] " + received);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}



