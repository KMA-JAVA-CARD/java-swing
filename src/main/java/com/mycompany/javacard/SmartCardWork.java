package com.mycompany.javacard;

import javax.smartcardio.*;
import java.util.List;
import java.nio.charset.StandardCharsets;

public class SmartCardWork {

    private Card card;
    private CardChannel channel;

    // AID của Applet (Phải khớp với file .jcproj của bạn)
    // Trong file xml bạn gửi: A0 00 00 00 62 03 01 0A 01
    private final byte[] AID_APPLET = new byte[]{
        (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0A, 0x01,};

    // Kết nối thẻ
    public boolean connectCard() {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
                return false;
            }

            // Kết nối vào terminal đầu tiên (Thường là simulator hoặc đầu đọc thật)
            CardTerminal terminal = terminals.get(0);
            card = terminal.connect("*"); // Kết nối giao thức bất kỳ
            channel = card.getBasicChannel();

            // Gửi lệnh SELECT Applet
            CommandAPDU cmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, AID_APPLET);
            ResponseAPDU res = channel.transmit(cmd);
            return res.getSW() == 0x9000;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    public void disconnect() {
        try {
            if (card != null) {
                card.disconnect(false);
            }
        } catch (Exception e) {
        }
    }

    // 1. Chức năng ĐĂNG KÝ
    public boolean register(String pin, String fullName, String dob, String province, String phone) {
        try {
            // Nối chuỗi thông tin bằng dấu ngăn cách "|"
            String dataString = fullName + "|" + dob + "|" + province + "|" + phone;

            byte[] pinBytes = pin.getBytes(StandardCharsets.ISO_8859_1);
            byte[] dataBytes = dataString.getBytes(StandardCharsets.UTF_8);
            byte pinLen = (byte) pinBytes.length;

            // Tạo mảng dữ liệu gửi đi: [LenPin] + [PinBytes] + [DataBytes]
            byte[] payload = new byte[1 + pinBytes.length + dataBytes.length];
            payload[0] = pinLen;
            System.arraycopy(pinBytes, 0, payload, 1, pinLen);
            System.arraycopy(dataBytes, 0, payload, 1 + pinLen, dataBytes.length);

            // Gửi lệnh INS_REGISTER (0x01)
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x01, 0x00, 0x00, payload);
            ResponseAPDU res = channel.transmit(cmd);

            return res.getSW() == 0x9000;

        } catch (Exception e) {
            e.printStackTrace();
            return false;
        }
    }

    // 2. Chức năng LOGIN (Verify PIN)
    public boolean verifyPin(String pin) {
        try {
            byte[] pinBytes = pin.getBytes(StandardCharsets.ISO_8859_1);

            // Gửi lệnh INS_VERIFY (0x02)
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x02, 0x00, 0x00, pinBytes);
            ResponseAPDU res = channel.transmit(cmd);

            return res.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

    // 3. Chức năng LẤY THÔNG TIN
    public String getUserInfo() {
        try {
            // Gửi lệnh INS_GET_INFO (0x03)
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x03, 0x00, 0x00, 256);
            ResponseAPDU res = channel.transmit(cmd);

            if (res.getSW() == 0x9000) {
                // Convert byte[] về String
                return new String(res.getData(), StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public boolean changePin(String newPin) {
        try {
            byte[] pinBytes = newPin.getBytes(StandardCharsets.ISO_8859_1);

            // Gửi lệnh INS_CHANGE_PIN (0x04)
            // P1, P2 = 0x00
            // Data = [PIN mới]
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x04, 0x00, 0x00, pinBytes);
            ResponseAPDU res = channel.transmit(cmd);

            return res.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }

// Thêm hàm unblock pin (cho nút Reset thẻ nếu cần)
    public boolean unblockPin() {
        try {
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x05, 0x00, 0x00);
            ResponseAPDU res = channel.transmit(cmd);
            return res.getSW() == 0x9000;
        } catch (Exception e) {
            return false;
        }
    }
}
