package com.parishod.watomatic.network.model.openai;

import com.google.gson.annotations.SerializedName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class OpenAIRequest {
    private String model;
    private List<Message> messages;

    @SerializedName("response_format")
    private Map<String, String> responseFormat;

    public OpenAIRequest(String model, List<Message> messages) {
        this.model = model;
        this.messages = messages;
    }

    // Getters and setters
    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }
    public List<Message> getMessages() { return messages; }
    public void setMessages(List<Message> messages) { this.messages = messages; }

    public Map<String, String> getResponseFormat() { return responseFormat; }
    public void setResponseFormat(Map<String, String> responseFormat) { this.responseFormat = responseFormat; }

    /** OpenAI-compatible JSON-mode hint. Honored by GPT, ignored harmlessly by most others. */
    public void setResponseFormatJsonObject() {
        Map<String, String> rf = new HashMap<>();
        rf.put("type", "json_object");
        this.responseFormat = rf;
    }
}
