package cn.edu.zime.iot.client;

import cn.edu.zime.iot.handler.EchoUDPClientHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;
import java.util.Scanner;

public class EchoUDPClient {
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 9000;
    private final EventLoopGroup group;
    private Channel channel;

    public EchoUDPClient() {
        this.group = new NioEventLoopGroup();
    }

    public void start() {
        try {
            initializeChannel();
            startMessageLoop();
        } catch (Exception e) {
            System.err.println("UDP客户端启动失败: " + e.getMessage());
            e.printStackTrace();
        } finally {
            shutdown();
        }
    }

    private void initializeChannel() throws InterruptedException {
        Bootstrap b = new Bootstrap();
        b.group(group)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .handler(new ChannelInitializer<NioDatagramChannel>() {
                    @Override
                    protected void initChannel(NioDatagramChannel ch) {
                        ch.pipeline().addLast(new EchoUDPClientHandler());
                    }
                });

        channel = b.bind(0).sync().channel();
        System.out.println("UDP客户端已启动，输入消息发送给服务器 (输入'exit'退出)");
    }

    private void startMessageLoop() {
        InetSocketAddress serverAddress = new InetSocketAddress(HOST, PORT);
        try (Scanner scanner = new Scanner(System.in)) {
            while (scanner.hasNextLine()) {
                String line = scanner.nextLine();
                if ("exit".equalsIgnoreCase(line)) {
                    break;
                }
                sendMessage(line, serverAddress);
            }
        }
    }

    private void sendMessage(String message, InetSocketAddress serverAddress) {
        try {
            ByteBuf buf = channel.alloc().buffer();
            buf.writeCharSequence(message, CharsetUtil.UTF_8);
            DatagramPacket packet = new DatagramPacket(buf, serverAddress);
            channel.writeAndFlush(packet);
            System.out.println("已发送: " + message);
        } catch (Exception e) {
            System.err.println("发送消息失败: " + e.getMessage());
        }
    }

    private void shutdown() {
        if (channel != null) {
            channel.close();
        }
        group.shutdownGracefully();
    }

    public static void main(String[] args) {
        new EchoUDPClient().start();
    }
}



















