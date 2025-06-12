package cn.edu.zime.iot.server;

import cn.edu.zime.iot.handler.EchoUDPServerHandler;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class EchoUDPServer {
    private static EchoUDPServer s_instance = null;

    public static final int PORT = 9000;

    private EchoUDPServer(){

    }

    public static EchoUDPServer getInstance(){
        if(s_instance == null){
            s_instance = new EchoUDPServer();
        }

        return s_instance;
    }

    public void start(){
        EventLoopGroup group = new NioEventLoopGroup();
        try {
            // UDP使用Bootstrap而不是ServerBootstrap
            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioDatagramChannel.class)  // 使用UDP通道
                    .option(ChannelOption.SO_BROADCAST, true)  // 允许广播
                    .handler(new ChannelInitializer<NioDatagramChannel>() {
                        @Override
                        protected void initChannel(NioDatagramChannel ch) {
                            ChannelPipeline pipeline = ch.pipeline();
                            pipeline.addLast(new EchoUDPServerHandler());
                        }
                    });

            // 绑定端口（UDP没有连接概念，只有绑定）
            ChannelFuture f = b.bind(PORT).sync();
            System.out.println("UDP Echo服务器启动，监听端口: " + PORT);

            f.channel().closeFuture().sync();
        }
        catch(InterruptedException e){
            e.printStackTrace();
        } finally {
            group.shutdownGracefully();
        }
    }
}
