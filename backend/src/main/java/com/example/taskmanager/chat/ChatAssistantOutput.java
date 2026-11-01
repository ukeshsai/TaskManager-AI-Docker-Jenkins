package com.example.taskmanager.chat;

import java.util.List;

public record ChatAssistantOutput(String reply, List<ChatJobAction> actions) {
}
