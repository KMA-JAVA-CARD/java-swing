package com.mycompany.javacard;

import com.google.gson.Gson;
import okhttp3.*;

import java.io.IOException;

public class APIService {
    private static final String BASE_URL = "http://localhost:8000"; // Nestjs server
    private final OkHttpClient client;
    private final Gson gson;

    public APIService() {
        this.client = new OkHttpClient();
        this.gson = new Gson();
    }

    // DTO để gửi lên Server (khớp với RegisterCardDto của NestJS)
    public static class RegisterRequest {
        String cardSerial;
        String publicKey;
        String fullName;
        String phone;
        String email;
        String address;
        String dob;

        public RegisterRequest(String cardId, String pubKey, String name, String dob, String address, String phone, String email) {
            this.cardSerial = cardId;
            this.publicKey = pubKey;
            this.fullName = name;
            this.dob = dob;
            this.address = address;
            this.phone = phone;
            this.email = email;
        }
    }

    public boolean registerCard(String cardId, String publicKey, String name, String dob, String address, String phone, String email) {
        RegisterRequest reqData = new RegisterRequest(cardId, publicKey, name, dob, address, phone,  email);
        String json = gson.toJson(reqData);

        RequestBody body = RequestBody.create(json, MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/cards/register")
                .post(body)
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (response.isSuccessful()) {
                System.out.println("Backend Response: " + response.body().string());
                return true;
            } else {
                System.out.println("Backend Error: " + response.code() + " - " + response.body().string());
                return false;
            }
        } catch (IOException e) {
            e.printStackTrace();
            return false;
        }
    }
}
