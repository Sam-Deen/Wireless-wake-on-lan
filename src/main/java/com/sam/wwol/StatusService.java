package com.sam.wwol;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class StatusService {

    @Value("${target.ip}")
    private String targetIp;

    private volatile String status = "unknown";

    public boolean ping() {
        try {
            Process p = new ProcessBuilder("ping", "-c", "1", "-W", "1", targetIp)
                    .redirectErrorStream(true)
                    .start();
            int code = p.waitFor();
            return code == 0;
        } catch (Exception e) {
            return false;
        }
    }

    public void updateStatus() {
        status = ping() ? "on" : "off";
    }

    public String getStatus() {
        return status;
    }
}
