package com.parishod.watomatic.model.classifier;

import java.util.ArrayList;
import java.util.List;

/**
 * User-supplied context gathered during onboarding. Sent verbatim to the classifier LLM as part
 * of the system prompt so the model can reason as the user would.
 *
 * <p>Persisted as a single Gson-serialized JSON blob in EncryptedSharedPreferences (contains
 * personal context like names of family/colleagues — encryption is cheap insurance).</p>
 */
public class UserProfile {

    public enum Tone { CASUAL, PROFESSIONAL, BRIEF }

    private String displayName;
    private String occupation;
    private int workingHoursStartMinuteOfDay = 9 * 60;
    private int workingHoursEndMinuteOfDay = 18 * 60;
    private Tone tone = Tone.CASUAL;
    private String communicationStyle;
    private List<KeyRelationship> keyRelationships = new ArrayList<>();
    private String additionalContext;

    public UserProfile() {}

    /** Considered complete enough to drive classification once name and occupation are filled. */
    public boolean isComplete() {
        return displayName != null && !displayName.trim().isEmpty()
                && occupation != null && !occupation.trim().isEmpty();
    }

    public String getDisplayName() { return displayName; }
    public void setDisplayName(String displayName) { this.displayName = displayName; }

    public String getOccupation() { return occupation; }
    public void setOccupation(String occupation) { this.occupation = occupation; }

    public int getWorkingHoursStartMinuteOfDay() { return workingHoursStartMinuteOfDay; }
    public void setWorkingHoursStartMinuteOfDay(int value) { this.workingHoursStartMinuteOfDay = value; }

    public int getWorkingHoursEndMinuteOfDay() { return workingHoursEndMinuteOfDay; }
    public void setWorkingHoursEndMinuteOfDay(int value) { this.workingHoursEndMinuteOfDay = value; }

    public Tone getTone() { return tone; }
    public void setTone(Tone tone) { this.tone = tone; }

    public String getCommunicationStyle() { return communicationStyle; }
    public void setCommunicationStyle(String communicationStyle) { this.communicationStyle = communicationStyle; }

    public List<KeyRelationship> getKeyRelationships() {
        if (keyRelationships == null) keyRelationships = new ArrayList<>();
        return keyRelationships;
    }
    public void setKeyRelationships(List<KeyRelationship> keyRelationships) {
        this.keyRelationships = keyRelationships;
    }

    public String getAdditionalContext() { return additionalContext; }
    public void setAdditionalContext(String additionalContext) { this.additionalContext = additionalContext; }
}
