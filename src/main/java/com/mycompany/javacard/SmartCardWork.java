package com.mycompany.javacard;

import javax.smartcardio.*;
import java.util.List;
import java.nio.charset.StandardCharsets;

public class SmartCardWork {

    private Card card;
    private CardChannel channel;

    // AID của Applet (Phải khớp với file .jcproj của bạn)
    // A0 00 00 00 62 03 01 0A 01
    private final byte[] AID_APPLET = new byte[]{
            (byte) 0xA0, 0x00, 0x00, 0x00, 0x62, 0x03, 0x01, 0x0A, 0x01, 0x00
    };

    // Kết nối thẻ
    public boolean connectCard() {
        try {
            TerminalFactory factory = TerminalFactory.getDefault();
            List<CardTerminal> terminals = factory.terminals().list();

            if (terminals.isEmpty()) {
                System.out.println("Lỗi: Không tìm thấy đầu đọc thẻ nào!");
                return false;
            }

            System.out.println("Đang quét " + terminals.size() + " đầu đọc...");

            // --- CHIẾN THUẬT MỚI: RÀ SOÁT TỪNG CÁI ---
            for (CardTerminal t : terminals) {
                try {
                    System.out.println("Kiểm tra: " + t.getName());

                    // Chỉ quan tâm đầu đọc của Simulator (để tránh connect nhầm cái khác)
                    // Nếu huynh dùng thẻ thật thì bỏ dòng if này đi
                    if (t.getName().toUpperCase().contains("JAVACOS") ||
                            t.getName().toUpperCase().contains("VIRTUAL")) {

                        // QUAN TRỌNG: Hỏi xem có thẻ trong đó không?
                        if (t.isCardPresent()) {
                            System.out.println("=> PHÁT HIỆN THẺ TẠI: " + t.getName());

                            // Kết nối ngay!
                            card = t.connect("*");
                            channel = card.getBasicChannel();

                            // Gửi lệnh SELECT Applet ngay để kiểm tra
                            CommandAPDU cmd = new CommandAPDU(0x00, 0xA4, 0x04, 0x00, AID_APPLET);
                            ResponseAPDU res = channel.transmit(cmd);

                            if (res.getSW() == 0x9000) {
                                System.out.println("=> Select Applet thành công! Đã kết nối.");
                                return true; // Tìm thấy và kết nối xong, thoát luôn!
                            } else {
                                System.out.println("=> Có thẻ nhưng Select Applet thất bại (SW: "
                                        + Integer.toHexString(res.getSW()) + "). Thử cái tiếp...");
                                // Có thẻ nhưng không phải thẻ của mình, ngắt kết nối để thử cái khác
                                card.disconnect(false);
                            }
                        } else {
                            System.out.println("=> Không có thẻ.");
                        }
                    }
                } catch (Exception e) {
                    System.out.println("=> Lỗi khi kiểm tra đầu đọc này: " + e.getMessage());
                    // Bỏ qua lỗi, đi tiếp sang đầu đọc sau
                }
            }

            System.out.println("Đã quét hết danh sách mà không kết nối được thẻ nào!");
            return false;

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

    // Helper: Chuyển byte[] sang Hex String (để gửi lên Backend)
    private static String bytesToHex(byte[] bytes) {
        StringBuilder sb = new StringBuilder();
        for (byte b : bytes) {
            sb.append(String.format("%02X", b));
        }
        return sb.toString();
    }

    public String getCardID() {
        try {
            // Gửi lệnh INS_GET_CARD_ID (0x06)
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x06, 0x00, 0x00, 256);
            ResponseAPDU res = channel.transmit(cmd);

            if (res.getSW() == 0x9000) {
                // Chuyển 8 byte hex sang String
                return bytesToHex(res.getData());
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    // 1. Chức năng ĐĂNG KÝ
    public String register(String pin, String fullName, String dob, String province, String phone) {
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

            if (res.getSW() == 0x9000) {
                // Thẻ trả về: [Len Mod][Modulus][Len Exp][Exponent]
                byte[] data = res.getData();

                // Lấy 8 byte đầu làm ID
                 byte[] idBytes = new byte[8];
                 System.arraycopy(data, 0, idBytes, 0, 8);
                 String cardId = bytesToHex(idBytes);

                // Phần còn lại là Public Key (Modulus + Exponent)
                 byte[] keyBytes = new byte[data.length - 8];
                 System.arraycopy(data, 8, keyBytes, 0, data.length - 8);
                 String publicKey = bytesToHex(keyBytes);

                 return cardId + "|" + publicKey;

                // return bytesToHex(data);
            } else {
                return null;
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // 2. Chức năng LOGIN (Verify PIN)
    public int verifyPin(String pin) {
        try {
            byte[] pinBytes = pin.getBytes(StandardCharsets.ISO_8859_1);

            // Gửi lệnh INS_VERIFY (0x02)
            CommandAPDU cmd = new CommandAPDU(0xA0, 0x02, 0x00, 0x00, pinBytes);
            ResponseAPDU res = channel.transmit(cmd);

            return res.getSW();
        } catch (Exception e) {
            return 0;
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
