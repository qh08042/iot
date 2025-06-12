package cn.edu.zime.iot.client;

import cn.edu.zime.iot.handler.EchoClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.channel.*;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Scanner;
import java.util.Set;


public class EchoClient {
    public static final String HOST = "127.0.0.1";
    public static final int PORT = 9000;

    private final String clientId;
    private Channel channel;
    private Set<String> onlineClients = new HashSet<>();

    public EchoClient(String clientId) {
        this.clientId = clientId;
    }

    public void start() {
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(new StringDecoder());
                            p.addLast(new StringEncoder());
                            p.addLast(new EchoClientHandler(EchoClient.this));
                        }
                    });

            ChannelFuture f = b.connect(HOST, PORT).sync();
            System.out.println(clientId + " 已连接到服务器，输入命令（注册ID: reg|你的ID, 注销: unreg|你的ID, 获取列表: list, 广播: broadcast|消息, 私聊: chat|好友ID|消息, 退出: exit）");

            channel = f.channel();

            // 注册客户端
            channel.writeAndFlush("reg|" + clientId);

            Scanner scanner = new Scanner(System.in);
            while (true) {
                if (!scanner.hasNextLine()) break;
                String line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line)) {
                    // 注销后退出
                    channel.writeAndFlush("unreg|" + clientId);
                    break;
                }
                channel.writeAndFlush(line);
            }

            channel.closeFuture().sync();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }

    public void handleClientList(String clients) {
        Set<String> newClients = new HashSet<>(Arrays.asList(clients.split(",")));
        newClients.remove(clientId); // 移除自己

        // 检测离线的客户端
        Set<String> offlineClients = new HashSet<>(onlineClients);
        offlineClients.removeAll(newClients);

        // 检测新上线的客户端
        Set<String> newOnlineClients = new HashSet<>(newClients);
        newOnlineClients.removeAll(onlineClients);

        // 更新在线客户端列表
        onlineClients = newClients;

        // 显示离线提醒
        for (String offlineClient : offlineClients) {
            System.out.println("[系统提醒] " + offlineClient + " 已离线");
        }

        // 显示新上线提醒
        for (String newClient : newOnlineClients) {
            System.out.println("[系统提醒] " + newClient + " 已上线");
        }

        // 显示当前在线用户列表
        if (!newClients.isEmpty()) {
            System.out.println("\n当前在线用户列表：");
            for (String client : newClients) {
                System.out.println("- " + client);
            }
        } else {
            System.out.println("\n当前没有其他用户在线");
        }
    }

    public String getClientId() {
        return clientId;
    }
}

