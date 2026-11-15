package com.roomarchive.server;

import android.content.ComponentName;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telecom.Connection;
import android.telecom.ConnectionRequest;
import android.telecom.ConnectionService;
import android.telecom.DisconnectCause;
import android.telecom.PhoneAccount;
import android.telecom.PhoneAccountHandle;
import android.telecom.TelecomManager;

/**
 * 真正注册到 Telecom 的模拟通话 ConnectionService。
 * PhoneAccount 使用 CAPABILITY_SELF_MANAGED，来电 UI 由本应用自己展示，
 * 但通话状态、接听/挂断都走系统 Telecom 通道，因此更像真实来电。
 */
public class SimCallConnectionService extends ConnectionService {

    public static final String ACCOUNT_ID = "ik_sim_call";
    private static SimConnection current;
    private static String pendingName = "张三";
    private static String pendingNumber = "13800138000";

    public static PhoneAccountHandle handle(android.content.Context c) {
        return new PhoneAccountHandle(new ComponentName(c, SimCallConnectionService.class), ACCOUNT_ID);
    }

    public static void setPending(String name, String number) {
        pendingName = name;
        pendingNumber = number;
    }

    public static boolean answer() {
        if (current == null) return false;
        current.onAnswer();
        return true;
    }

    public static void disconnect() {
        if (current != null) {
            current.setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            current.destroy();
            current = null;
        }
    }

    private void showUi() {
        try {
            Intent it = new Intent(this, SimCallActivity.class);
            it.putExtra("incoming", true);
            it.putExtra("name", pendingName);
            it.putExtra("number", pendingNumber);
            it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
            startActivity(it);
        } catch (Throwable ignored) {}
    }

    @Override
    public Connection onCreateIncomingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        SimConnection c = new SimConnection();
        c.setConnectionProperties(Build.VERSION.SDK_INT >= 26
                ? Connection.PROPERTY_SELF_MANAGED : 0);
        c.setConnectionCapabilities(Connection.CAPABILITY_MUTE | Connection.CAPABILITY_SUPPORT_HOLD);
        String number = pendingNumber;
        Uri addr = request.getAddress();
        if (addr != null && addr.getSchemeSpecificPart() != null && !addr.getSchemeSpecificPart().isEmpty()) {
            number = addr.getSchemeSpecificPart();
        }
        c.setAddress(Uri.fromParts("tel", number, null), TelecomManager.PRESENTATION_ALLOWED);
        c.setCallerDisplayName(pendingName, TelecomManager.PRESENTATION_ALLOWED);
        c.setRinging();
        current = c;
        showUi();
        return c;
    }

    @Override
    public Connection onCreateOutgoingConnection(PhoneAccountHandle account, ConnectionRequest request) {
        return null;
    }

    public static class SimConnection extends Connection {
        @Override public void onAnswer() {
            setActive();
        }

        @Override public void onReject() {
            setDisconnected(new DisconnectCause(DisconnectCause.REJECTED));
            destroy();
        }

        @Override public void onDisconnect() {
            setDisconnected(new DisconnectCause(DisconnectCause.LOCAL));
            destroy();
        }

        @Override public void onAbort() {
            setDisconnected(new DisconnectCause(DisconnectCause.CANCELED));
            destroy();
        }
    }
}
