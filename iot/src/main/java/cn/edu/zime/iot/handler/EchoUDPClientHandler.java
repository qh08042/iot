package cn.edu.zime.iot.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;

public class EchoUDPClientHandler extends SimpleChannelInboundHandler<DatagramPacket> {
    private static final String MESSAGE_FORMAT = "客户端收到 [%s:%d]: %s";

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        try {
            ByteBuf content = packet.content();
            String response = content.toString(CharsetUtil.UTF_8);
            InetSocketAddress sender = packet.sender();
            
            System.out.printf(MESSAGE_FORMAT + "%n",
                    sender.getAddress().getHostAddress(),
                    sender.getPort(),
                    response);
        } catch (Exception e) {
            System.err.println("处理UDP消息时发生错误: " + e.getMessage());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        System.err.println("UDP通道异常: " + cause.getMessage());
        cause.printStackTrace();
        ctx.close();
    }
}

