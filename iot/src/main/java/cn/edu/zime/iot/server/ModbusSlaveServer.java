package cn.edu.zime.iot.server;

import cn.edu.zime.iot.handler.ModbusSlaveHandler;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.LengthFieldBasedFrameDecoder;

public class ModbusSlaveServer {
    private static ModbusSlaveServer s_instance = null;

    private static final int MODBUS_PORT = 502; // Modbus 标准端口

    private static final int MAX_FRAME_LENGTH = 256; // 最大帧长度

    public ModbusSlaveServer(){

    }

    //创建单例
    public static ModbusSlaveServer getInstance(){
        if( s_instance == null ){
            s_instance = new ModbusSlaveServer();
        }

        return s_instance;
    }

    //netty核心方法，使用netty框架启动TCP服务器。
    public void start(){
        EventLoopGroup bossGroup = new NioEventLoopGroup(1);
        EventLoopGroup workerGroup = new NioEventLoopGroup();

        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(bossGroup, workerGroup)
                    .channel(NioServerSocketChannel.class)
                    .option(ChannelOption.SO_BACKLOG, 128)
                    .childOption(ChannelOption.SO_KEEPALIVE, true)
                    .childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();

                            // 处理Modbus TCP帧头（MBAP头）
                            p.addLast(new LengthFieldBasedFrameDecoder(
                                    MAX_FRAME_LENGTH,
                                    4,  // lengthFieldOffset: MBAP头中长度字段的偏移量
                                    2,  // lengthFieldLength: 长度字段占2字节
                                    0, // lengthAdjustment: 调整长度（去除MBAP头的前6字节）
                                    0,  // initialBytesToStrip: 不剥离任何字节
                                    true));


                            // 添加Modbus协议处理器
                            p.addLast(new ModbusSlaveHandler());
                        }
                    });

            ChannelFuture f = b.bind(MODBUS_PORT).sync();
            System.out.println("Modbus从站已启动，监听端口: " + MODBUS_PORT);

            f.channel().closeFuture().sync();
        }
        catch (InterruptedException e){
            e.printStackTrace();
        }
        finally {
            workerGroup.shutdownGracefully();
            bossGroup.shutdownGracefully();
        }
    }
}
