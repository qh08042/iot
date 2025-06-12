package cn.edu.zime.iot;

import cn.edu.zime.iot.server.EchoServer;
import cn.edu.zime.iot.server.EchoUDPServer;
import cn.edu.zime.iot.server.ModbusSlaveServer;

public class MainServerApp {
    public static void main(String[] args){
        // 同时启动 TCP 和 UDP 服务
        new Thread(() -> {
            EchoServer server = EchoServer.getInstance();
            server.start();
        }).start();

        new Thread(() -> {
            EchoUDPServer udpServer = EchoUDPServer.getInstance();
            udpServer.start();
        }).start();

        new Thread(() -> {
            ModbusSlaveServer server = ModbusSlaveServer.getInstance();
            server.start();
        }).start();
    }
}



