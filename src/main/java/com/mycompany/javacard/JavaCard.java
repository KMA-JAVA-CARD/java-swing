/*
 * Click nbfs://nbhost/SystemFileSystem/Templates/Licenses/license-default.txt to change this license
 */

package com.mycompany.javacard;

import java.io.File;
import javax.swing.SwingUtilities;

/**
 * Class chính để chạy ứng dụng
 * @author huyho
 */
public class JavaCard {

    public static void main(String[] args) {
       SwingUtilities.invokeLater(() -> {
            System.out.println("Ung dung khoi dong...");
            new login().setVisible(true); 
        });
}
}