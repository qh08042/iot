package cn.edu.zime.iot;

import cn.edu.zime.iot.client.EchoClient;
import cn.edu.zime.iot.client.EchoUDPClient;
import cn.edu.zime.iot.server.ModbusSlaveServer;

import java.util.Scanner;

public class MainClientA {
    public static void main(String[] args){
        Scanner scanner = new Scanner(System.in);
        System.out.println("请选择通信方式：");
        System.out.println("1. TCP 聊天");
        System.out.println("2. UDP 消息测试");
        System.out.println("3. 电源设备模拟");
        System.out.print("输入1或2或3: ");

        String choice = scanner.nextLine().trim();

        if ("1".equals(choice)) {
            System.out.println("欢迎使用客户端A");
            System.out.print("请输入您的用户名: ");
            String clientId = scanner.nextLine().trim();

            if (clientId.isEmpty()) {
                System.out.println("用户名不能为空，使用默认用户名: ClientA");
                clientId = "ClientA";
            }

            EchoClient client = new EchoClient(clientId);
            client.start();
        } else if ("2".equals(choice)) {
            EchoUDPClient client = new EchoUDPClient();
            client.start();
        }else if ("3".equals(choice)) {

            ModbusSlaveServer server=new ModbusSlaveServer();
            server.start();
        }  else {
            System.out.println("无效选择，程序退出.");
        }

        scanner.close();
    }
}




