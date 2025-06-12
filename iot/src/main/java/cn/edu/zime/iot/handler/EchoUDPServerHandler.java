package cn.edu.zime.iot.handler;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import io.netty.util.CharsetUtil;

import java.net.InetSocketAddress;

public class EchoUDPServerHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        // 获取发送方地址
        InetSocketAddress sender = packet.sender();
        // 读取数据
        ByteBuf content = packet.content();
        String received = content.toString(CharsetUtil.UTF_8);

        System.out.printf("服务器收到 [%s:%d]: %s%n",
                sender.getAddress().getHostAddress(),
                sender.getPort(),
                received);

        // 创建回显数据包（原样返回）
        ByteBuf buf = ctx.alloc().buffer();
        buf.writeCharSequence(received, CharsetUtil.UTF_8);
        DatagramPacket response = new DatagramPacket(buf, sender);

        // 发送回显
        ctx.writeAndFlush(response);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }
}

