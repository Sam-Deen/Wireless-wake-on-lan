package com.sam.wwol;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.Session;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class PowerService {

    @Value("${target.mac}")
    private String mac;

    @Value("${target.ip}")
    private String ip;

    private final StatusService status;

    public PowerService(StatusService status) {
        this.status = status;
    }

    // Validate the same packet configuration and local transport used by power/on.
    // Health checks never transmit a magic packet or issue a shutdown command.
    public boolean wakeTransportAvailable() {
        try {
            parseMac(mac);
            try (var socket = new java.net.DatagramSocket()) {
                socket.setBroadcast(true);
            }
            return true;
        } catch (Exception error) {
            return false;
        }
    }

    public String turnOn() {
        sendWakeOnLan(mac);
        sleep(7000);
        return status.ping() ? "on" : "off";
    }

    public String turnOff() {

        runSsh("shutdown /s");
        sleep(7000);
        return status.ping() ? "on" : "off";
    }

private void sendWakeOnLan(String macAddress) {
    try {
        byte[] macBytes = parseMac(macAddress);
        byte[] packet = new byte[6 + 16 * macBytes.length];

        for (int i = 0; i < 6; i++) {
            packet[i] = (byte) 0xFF;
        }

        for (int i = 6; i < packet.length; i += macBytes.length) {
            System.arraycopy(macBytes, 0, packet, i, macBytes.length);
        }

        var address = java.net.InetAddress.getByName("255.255.255.255");

        try (var socket = new java.net.DatagramSocket()) {
            socket.setBroadcast(true);

            var datagram = new java.net.DatagramPacket(
                packet,
                packet.length,
                address,
                9
            );

            socket.send(datagram);
        }

    } catch (Exception e) {
        throw new RuntimeException("Failed to send Wake on LAN packet", e);
    }
}

private byte[] parseMac(String macAddress) {
    String cleaned = macAddress.replace(":", "").replace("-", "");

    if (cleaned.length() != 12) {
        throw new IllegalArgumentException(
            "Invalid MAC address: " + macAddress
        );
    }

    byte[] bytes = new byte[6];

    for (int i = 0; i < 6; i++) {
        bytes[i] = (byte) Integer.parseInt(
            cleaned.substring(i * 2, i * 2 + 2),
            16
        );
    }

    return bytes;
}

    private void runSsh(String command) {
    Session session = null;
    ChannelExec channel = null;

    try {
        JSch jsch = new JSch();

        session = jsch.getSession("sshuser", ip, 22);
        session.setPassword("Yvonne200302");

        session.setConfig("StrictHostKeyChecking", "no");
        session.setConfig("PreferredAuthentications", "password");
        session.setConfig("PubkeyAuthentication", "no");

        session.connect(10_000);

        channel = (ChannelExec) session.openChannel("exec");
        channel.setCommand(command);
        channel.setErrStream(System.err);

        channel.connect(5_000);

        while (!channel.isClosed()) {
            Thread.sleep(100);
        }

        int exitCode = channel.getExitStatus();

        if (exitCode != 0) {
            throw new IllegalStateException(
                "Shutdown command failed with exit code " + exitCode
            );
        }

    } catch (Exception e) {
        throw new RuntimeException("Failed to issue remote shutdown", e);
    } finally {
        if (channel != null && channel.isConnected()) {
            channel.disconnect();
        }

        if (session != null && session.isConnected()) {
            session.disconnect();
        }
    }
}

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException ignored) {}
    }
}
