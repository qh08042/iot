package cn.edu.zime.iot.server;

import cn.edu.zime.iot.handler.EchoServerHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.string.StringDecoder;
import io.netty.handler.codec.string.StringEncoder;

public class EchoServer {
    private static EchoServer s_instance = null;

    private final static int PORT = 9000;

    private EchoServer(){

    }

    public static EchoServer getInstance(){
        if (s_instance == null){
            s_instance = new EchoServer();
        }

        return s_instance;
    }

    public void start(){
        // 创建主从Reactor线程组
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);  // 接收连接
        EventLoopGroup workerGroup = new NioEventLoopGroup(); // 处理业务

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)  // 使用NIO传输
                    .option(ChannelOption.SO_BACKLOG, 128)  // 连接队列大小
                    .childOption(ChannelOption.SO_KEEPALIVE, true)  // 保持长连接
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            // 添加字符串编解码器
                            p.addLast(new StringDecoder());
                            p.addLast(new StringEncoder());
                            // 添加自定义处理器
                            p.addLast(new EchoServerHandler());
                        }
                    });

            // 绑定端口并启动服务器
            ChannelFuture f = b.bind(PORT).sync();
            System.out.println("Echo服务器启动，监听端口: " + PORT);

            // 等待服务器通道关闭
            f.channel().closeFuture().sync();
        }
        catch (InterruptedException e){
            e.printStackTrace();
        }
        finally {
            // 优雅关闭线程组
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
