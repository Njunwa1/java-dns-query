package com.company.app;

import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.List;


public class Main {
    private final static int TIME_OUT = 10000;
    private final static int PORT = 53;
    private final static int BUF_SIZE = 8192;

    public static void main(String[] args) {
        List<String> ipList = new ArrayList<String>();
        query("223.5.5.5", "www.taobao.com", ipList);
        for (String ip : ipList) {
            System.out.println(ip);
        }
    }

    private static void query(String dnsServerIP, String domainName, List<String> ipList) {
        try {
            DatagramSocket socket = new DatagramSocket(0);
            socket.setSoTimeout(TIME_OUT);

            ByteArrayOutputStream outBuf = new ByteArrayOutputStream(BUF_SIZE);
            DataOutputStream output = new DataOutputStream(outBuf);
            encodeDNSMessage(output, domainName);

            InetAddress host = InetAddress.getByName(dnsServerIP);
            DatagramPacket request = new DatagramPacket(outBuf.toByteArray(), outBuf.size(), host, PORT);

            socket.send(request);

            byte[] inBuf = new byte[BUF_SIZE];
            ByteArrayInputStream inBufArray = new ByteArrayInputStream(inBuf);
            DataInputStream input = new DataInputStream(inBufArray);
            DatagramPacket response = new DatagramPacket(inBuf, inBuf.length);

            socket.receive(response);

            decodeDNSMessage(input, ipList);
        } catch (SocketTimeoutException ex) {
            System.out.println("Timeout");
        } catch (IOException ex) {
            System.out.println("Unexpected IOException: " + ex);
        }
    }

    private static void encodeDNSMessage(DataOutputStream output, String domainName) {
        try {
            // transaction id
            output.writeShort(1);
            // flags
            output.writeShort(0x100);
            // number of queries
            output.writeShort(1);
            // answer, auth, other
            output.writeShort(0);
            output.writeShort(0);
            output.writeShort(0);

            encodeDomainName(output, domainName);

            // query type
            output.writeShort(1);
            // query class
            output.writeShort(1);

            output.flush();
        } catch (IOException ex) {
            System.out.println("Unexpected IOException: " + ex);
        }
    }

    private static void encodeDomainName(DataOutputStream output, String domainName) {
        try {
            for(String label : StringUtils.split(domainName, '.')) {
                output.writeByte((byte)label.length());
                output.write(label.getBytes());
            }
            output.writeByte(0);
        } catch (IOException ex) {
            System.out.println("Unexpected IOException: " + ex);
        }
    }

    private static void decodeDNSMessage(DataInputStream input, List<String> ipList) {
        try {
            // header
            // transaction id
            input.skip(2);
            // flags
            input.skip(2);
            // number of queries
            input.skip(2);
            // answer, auth, other
            short numberOfAnswer = input.readShort();
            input.skip(2);
            input.skip(2);

            // question record
            skipDomainName(input);
            // query type
            input.skip(2);
            // query class
            input.skip(2);

            // answer records
            for (int i = 0; i < numberOfAnswer; i++) {
                input.mark(1);
                byte ahead = input.readByte();
                input.reset();
                if ((ahead & 0xc0) == 0xc0) {
                    // compressed name
                    input.skip(2);
                } else {
                    skipDomainName(input);
                }

                // query type
                short type = input.readShort();
                // query class
                input.skip(2);
                // ttl
                input.skip(4);
                short addrLen = input.readShort();
                if (type == 1 && addrLen == 4) {
                    int addr = input.readInt();
                    ipList.add(longToIp(addr));
                } else {
                    input.skip(addrLen);
                }
            }
        } catch (IOException ex) {
            System.out.println("Unexpected IOException: " + ex);
        }
    }

    private static void skipDomainName(DataInputStream input) {
        try {
            byte labelLength = 0;
            do {
                labelLength = input.readByte();
                input.skip(labelLength);
            } while (labelLength != 0);
        } catch (IOException ex) {
            System.out.println("Unexpected IOException: " + ex);
        }
    }

    private static String longToIp(long ip) {
        return ((ip >> 24) & 0xFF) + "."
                + ((ip >> 16) & 0xFF) + "."
                + ((ip >> 8) & 0xFF) + "."
                + (ip & 0xFF);
    }
}