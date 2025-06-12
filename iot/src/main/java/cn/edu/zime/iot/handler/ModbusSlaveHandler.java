package cn.edu.zime.iot.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;

import java.util.Arrays;
import java.util.BitSet;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class ModbusSlaveHandler extends ChannelInboundHandlerAdapter {
    // Modbus 功能码
    private static final byte READ_COILS = 0x01;
    private static final byte READ_DISCRETE_INPUTS = 0x02;
    private static final byte READ_HOLDING_REGISTERS = 0x03;
    private static final byte READ_INPUT_REGISTERS = 0x04;
    private static final byte WRITE_SINGLE_COIL = 0x05;
    private static final byte WRITE_SINGLE_REGISTER = 0x06;
    private static final byte WRITE_MULTIPLE_COILS = 0x0F;
    private static final byte WRITE_MULTIPLE_REGISTERS = 0x10;

    // 异常码
    private static final byte ILLEGAL_FUNCTION = 0x01;
    private static final byte ILLEGAL_DATA_ADDRESS = 0x02;
    private static final byte ILLEGAL_DATA_VALUE = 0x03;
    private static final byte SLAVE_DEVICE_FAILURE = 0x04;

    // 模拟设备数据存储
    private static final BitSet coils = new BitSet(100); // 线圈状态 (0-9999)
    private static final BitSet discreteInputs = new BitSet(100); // 离散输入状态
    private static final short[] holdingRegisters = new short[100]; // 保持寄存器 (0-9999)
    private static final short[] inputRegisters = new short[100]; // 输入寄存器

    // 寄存器地址定义
    private static final int VOLTAGE_REGISTER = 0;    // 只读
    private static final int CURRENT_REGISTER = 1;     // 可读写
    private static final int SWITCH_COIL = 0;          // 可读写

    private static final Random random = new Random();
    private static short voltage = (short) (220 + random.nextInt(21)); // 220-240V
    private static short current = (short) random.nextInt(101);        // 0-100A
    private static boolean powerSwitch = random.nextBoolean();         // 随机开关状态
    static {
        // 初始化模拟数据
        for (int i = 0; i < holdingRegisters.length; i++) {
            holdingRegisters[i] = (short) (i * 10);
        }
        for (int i = 0; i < inputRegisters.length; i++) {
            inputRegisters[i] = (short) (i * 20);
        }

        // 设置一些线圈状态
        coils.set(0);
        coils.set(5);
        coils.set(10);
        coils.set(15);

        // 设置一些离散输入
        discreteInputs.set(1);
        discreteInputs.set(6);
        discreteInputs.set(11);
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        // 定时更新电压和电流数据（电压只读不修改）
        ctx.channel().eventLoop().scheduleAtFixedRate(() -> {
            voltage = (short) (220 + random.nextInt(21));
            current = (short) random.nextInt(101);
            System.out.printf("数据更新 - 电压: %dV, 电流: %dA, 开关: %s%n",
                    voltage, current, powerSwitch ? "ON" : "OFF");
        }, 0, 5, TimeUnit.SECONDS);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) {
        try {


            ByteBuf buf = (ByteBuf) msg;
            int len = buf.readableBytes();
            byte[] data = new byte[len];
            buf = buf.readBytes(data);

            if (len < 8) {
                // 无效的Modbus帧
                return;
            }

            // 解析MBAP头（Modbus Application Header）
            int transactionId = ((data[0] & 0xFF) << 8) | (data[1] & 0xFF);
            int protocolId = ((data[2] & 0xFF) << 8) | (data[3] & 0xFF);
            int length = ((data[4] & 0xFF) << 8) | (data[5] & 0xFF);
            byte unitId = data[6];
            byte functionCode = data[7];

            // 验证协议ID（应为0）
            if (protocolId != 0) {
                sendErrorResponse(ctx, transactionId, unitId, functionCode, ILLEGAL_FUNCTION);
                return;
            }

            // 根据功能码处理请求
            byte[] response = null;
            switch (functionCode) {
                case READ_COILS:
                    response = handleReadCoils(data, transactionId, unitId);
                    break;
                case READ_DISCRETE_INPUTS:
                    response = handleReadDiscreteInputs(data, transactionId, unitId);
                    break;
                case READ_HOLDING_REGISTERS:
                    response = handleReadHoldingRegisters(data, transactionId, unitId);
                    break;
                case READ_INPUT_REGISTERS:
                    response = handleReadInputRegisters(data, transactionId, unitId);
                    break;
                case WRITE_SINGLE_COIL:
                    response = handleWriteSingleCoil(data, transactionId, unitId);
                    break;
                case WRITE_SINGLE_REGISTER:
                    response = handleWriteSingleRegister(data, transactionId, unitId);
                    break;
                case WRITE_MULTIPLE_COILS:
                    response = handleWriteMultipleCoils(data, transactionId, unitId);
                    break;
                case WRITE_MULTIPLE_REGISTERS:
                    response = handleWriteMultipleRegisters(data, transactionId, unitId);
                    break;
                default:
                    sendErrorResponse(ctx, transactionId, unitId, functionCode, ILLEGAL_FUNCTION);
                    return;
            }

            if (response != null) {
                ByteBuf result = Unpooled.copiedBuffer(response);
                ctx.writeAndFlush(result);
            }

            System.out.println("模拟数据:");
            System.out.println("线圈状态: " + coils);
            System.out.println("离散输入: " + discreteInputs);
            System.out.println("保持寄存器: " + Arrays.toString(holdingRegisters));
            System.out.println("输入寄存器: " + Arrays.toString(inputRegisters));
        } finally {
            ReferenceCountUtil.release(msg);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        cause.printStackTrace();
        ctx.close();
    }

    // 处理读线圈请求 (0x01)
    private byte[] handleReadCoils(byte[] request, int transactionId, byte unitId) {
        int startAddress = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int quantity = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);

        // 只支持读取开关状态(地址0)
        if (startAddress != SWITCH_COIL || quantity != 1) {
            return sendErrorResponse(transactionId, unitId, READ_COILS, ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[9 + 1]; // 1字节足够
        buildMbapHeader(response, transactionId, (byte)4, unitId);
        response[7] = READ_COILS;
        response[8] = 1; // 字节数
        response[9] = powerSwitch ? (byte)0x01 : (byte)0x00;

        return response;
    }
    // 处理读离散输入请求 (0x02)
    private byte[] handleReadDiscreteInputs(byte[] request, int transactionId, byte unitId) {
        if (request.length < 12) return null;

        int startAddress = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int quantity = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);

        if (startAddress < 0 || startAddress + quantity > discreteInputs.size() ||
                quantity < 1 || quantity > 2000) {
            sendErrorResponse(transactionId, unitId, READ_DISCRETE_INPUTS, ILLEGAL_DATA_ADDRESS);
            return null;
        }

        int byteCount = (quantity + 7) / 8;
        byte[] response = new byte[9 + byteCount];

        buildMbapHeader(response, transactionId, (byte) (3 + byteCount), unitId);
        response[7] = READ_DISCRETE_INPUTS;
        response[8] = (byte) byteCount;

        for (int i = 0; i < quantity; i++) {
            if (discreteInputs.get(startAddress + i)) {
                response[9 + i/8] |= (1 << (i % 8));
            }
        }

        return response;
    }

    // 处理读保持寄存器请求 (0x03)
    private byte[] handleReadHoldingRegisters(byte[] request, int transactionId, byte unitId) {
        int startAddress = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int quantity = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);

        // 只允许读取地址0(电压)和1(电流)
        if ((startAddress != VOLTAGE_REGISTER && startAddress != CURRENT_REGISTER) ||
                quantity != 1) {
            return sendErrorResponse(transactionId, unitId, READ_HOLDING_REGISTERS, ILLEGAL_DATA_ADDRESS);
        }

        byte[] response = new byte[9 + 2]; // 1寄存器=2字节
        buildMbapHeader(response, transactionId, (byte)5, unitId);
        response[7] = READ_HOLDING_REGISTERS;
        response[8] = 2; // 字节数

        // 根据地址返回数据
        short value = (startAddress == VOLTAGE_REGISTER) ? voltage : current;
        response[9] = (byte)(value >> 8);
        response[10] = (byte)value;

        return response;
    }

    // 处理读输入寄存器请求 (0x04)
    private byte[] handleReadInputRegisters(byte[] request, int transactionId, byte unitId) {
        if (request.length < 12) return null;

        int startAddress = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int quantity = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);

        if (startAddress < 0 || startAddress + quantity > inputRegisters.length ||
                quantity < 1 || quantity > 125) {
            sendErrorResponse(transactionId, unitId, READ_INPUT_REGISTERS, ILLEGAL_DATA_ADDRESS);
            return null;
        }

        byte[] response = new byte[9 + quantity * 2];

        buildMbapHeader(response, transactionId, (byte) (3 + quantity * 2), unitId);
        response[7] = READ_INPUT_REGISTERS;
        response[8] = (byte) (quantity * 2);

        for (int i = 0; i < quantity; i++) {
            short value = inputRegisters[startAddress + i];
            response[9 + i*2] = (byte) (value >> 8);
            response[10 + i*2] = (byte) value;
        }

        return response;
    }

    // 处理写单个线圈请求 (0x05)
    private byte[] handleWriteSingleCoil(byte[] request, int transactionId, byte unitId) {
        int address = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int value = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);

        // 只允许修改开关状态(地址0)
        if (address != SWITCH_COIL || (value != 0x0000 && value != 0xFF00)) {
            return sendErrorResponse(transactionId, unitId, WRITE_SINGLE_COIL, ILLEGAL_DATA_ADDRESS);
        }

        // 更新开关状态
        powerSwitch = (value == 0xFF00);
        System.out.println("开关状态修改为: " + (powerSwitch ? "ON" : "OFF"));

        // 返回成功响应
        return Arrays.copyOf(request, 12); // 原样返回请求
    }
    // 处理写单个寄存器请求 (0x06)
    private byte[] handleWriteSingleRegister(byte[] request, int transactionId, byte unitId) {
        int address = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int value = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);

        // 只允许修改电流寄存器(地址1)
        if (address != CURRENT_REGISTER) {
            return sendErrorResponse(transactionId, unitId, WRITE_SINGLE_REGISTER, ILLEGAL_DATA_ADDRESS);
        }

        // 更新电流值
        current = (short) value;
        System.out.println("电流值修改为: " + current + "A");

        // 返回成功响应
        return Arrays.copyOf(request, 12); // 原样返回请求
    }
    // 处理写多个线圈请求 (0x0F)
    private byte[] handleWriteMultipleCoils(byte[] request, int transactionId, byte unitId) {
        if (request.length < 13) return null;

        int startAddress = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int quantity = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);
        int byteCount = request[12] & 0xFF;

        if (startAddress < 0 || startAddress + quantity > coils.size() ||
                quantity < 1 || quantity > 1968 ||
                byteCount != (quantity + 7) / 8) {
            sendErrorResponse(transactionId, unitId, WRITE_MULTIPLE_COILS, ILLEGAL_DATA_ADDRESS);
            return null;
        }

        // 更新线圈状态
        for (int i = 0; i < quantity; i++) {
            int byteIndex = 13 + i/8;
            int bitMask = 1 << (i % 8);
            boolean state = (request[byteIndex] & bitMask) != 0;
            coils.set(startAddress + i, state);
        }

        // 构建响应
        byte[] response = new byte[12];
        buildMbapHeader(response, transactionId, (byte) 6, unitId);
        response[7] = WRITE_MULTIPLE_COILS;
        response[8] = (byte) (startAddress >> 8);
        response[9] = (byte) startAddress;
        response[10] = (byte) (quantity >> 8);
        response[11] = (byte) quantity;

        return response;
    }

    // 处理写多个寄存器请求 (0x10)
    private byte[] handleWriteMultipleRegisters(byte[] request, int transactionId, byte unitId) {
        if (request.length < 13) return null;

        int startAddress = ((request[8] & 0xFF) << 8) | (request[9] & 0xFF);
        int quantity = ((request[10] & 0xFF) << 8) | (request[11] & 0xFF);
        int byteCount = request[12] & 0xFF;

        if (startAddress < 0 || startAddress + quantity > holdingRegisters.length ||
                quantity < 1 || quantity > 123 ||
                byteCount != quantity * 2) {
            sendErrorResponse(transactionId, unitId, WRITE_MULTIPLE_REGISTERS, ILLEGAL_DATA_ADDRESS);
            return null;
        }

        // 更新寄存器值
        for (int i = 0; i < quantity; i++) {
            int offset = 13 + i*2;
            short value = (short) (((request[offset] & 0xFF) << 8) | (request[offset+1] & 0xFF));
            holdingRegisters[startAddress + i] = value;
        }

        // 构建响应
        byte[] response = new byte[12];
        buildMbapHeader(response, transactionId, (byte) 6, unitId);
        response[7] = WRITE_MULTIPLE_REGISTERS;
        response[8] = (byte) (startAddress >> 8);
        response[9] = (byte) startAddress;
        response[10] = (byte) (quantity >> 8);
        response[11] = (byte) quantity;

        return response;
    }

    // 构建MBAP头
    private void buildMbapHeader(byte[] response, int transactionId, byte length, byte unitId) {
        // 事务ID
        response[0] = (byte) (transactionId >> 8);
        response[1] = (byte) transactionId;

        // 协议ID (0 for Modbus)
        response[2] = 0;
        response[3] = 0;

        // 长度 (包括单元标识符和PDU)
        response[4] = 0;
        response[5] = (byte) (length + 1); // +1 for unit identifier

        // 单元ID
        response[6] = unitId;
    }

    // 发送错误响应
    private void sendErrorResponse(ChannelHandlerContext ctx, int transactionId, byte unitId,
                                   byte functionCode, byte exceptionCode) {
        byte[] response = new byte[9];

        // MBAP头
        buildMbapHeader(response, transactionId, (byte) 3, unitId);

        // 功能码 (设置最高位表示异常)
        response[7] = (byte) (functionCode | 0x80);

        // 异常码
        response[8] = exceptionCode;

        ctx.writeAndFlush(response);
    }

    private byte[] sendErrorResponse(int transactionId, byte unitId, byte functionCode, byte exceptionCode) {
        byte[] response = new byte[9];
        buildMbapHeader(response, transactionId, (byte) 3, unitId);
        response[7] = (byte) (functionCode | 0x80);
        response[8] = exceptionCode;
        return response;
    }
}
