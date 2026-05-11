package com.parishod.watomatic.network.model.backend;

import com.google.gson.annotations.SerializedName;
import com.parishod.watomatic.model.classifier.ClassificationCategory;
import com.parishod.watomatic.model.classifier.KeyRelationship;
import com.parishod.watomatic.model.classifier.UserProfile;

import java.util.ArrayList;
import java.util.List;

public class ClassifyRequest {
    @SerializedName("user_id") public String userId;
    @SerializedName("sender") public String sender;
    @SerializedName("package") public String pkg;
    @SerializedName("message") public String message;
    @SerializedName("profile") public ProfilePayload profile;
    @SerializedName("categories") public List<CategoryPayload> categories;

    public static class ProfilePayload {
        @SerializedName("display_name") public String displayName = "";
        @SerializedName("occupation") public String occupation = "";
        @SerializedName("tone") public String tone = "CASUAL";
        @SerializedName("working_hours_start_min") public int workingHoursStartMin = 9 * 60;
        @SerializedName("working_hours_end_min") public int workingHoursEndMin = 18 * 60;
        @SerializedName("communication_style") public String communicationStyle = "";
        @SerializedName("key_relationships") public List<RelationshipPayload> keyRelationships = new ArrayList<>();
        @SerializedName("additional_context") public String additionalContext = "";

        public static ProfilePayload from(UserProfile p) {
            ProfilePayload out = new ProfilePayload();
            if (p == null) return out;
            out.displayName = nullSafe(p.getDisplayName());
            out.occupation = nullSafe(p.getOccupation());
            out.tone = p.getTone() != null ? p.getTone().name() : "CASUAL";
            out.workingHoursStartMin = p.getWorkingHoursStartMinuteOfDay();
            out.workingHoursEndMin = p.getWorkingHoursEndMinuteOfDay();
            out.communicationStyle = nullSafe(p.getCommunicationStyle());
            out.additionalContext = nullSafe(p.getAdditionalContext());
            if (p.getKeyRelationships() != null) {
                for (KeyRelationship kr : p.getKeyRelationships()) {
                    if (kr == null) continue;
                    RelationshipPayload r = new RelationshipPayload();
                    r.name = nullSafe(kr.getName());
                    r.role = nullSafe(kr.getRole());
                    out.keyRelationships.add(r);
                }
            }
            return out;
        }
    }

    public static class RelationshipPayload {
        @SerializedName("name") public String name = "";
        @SerializedName("role") public String role = "";
    }

    public static class CategoryPayload {
        @SerializedName("id") public String id;
        @SerializedName("name") public String name;
        @SerializedName("description") public String description;

        public static CategoryPayload from(ClassificationCategory c) {
            CategoryPayload p = new CategoryPayload();
            p.id = nullSafe(c.getId());
            p.name = nullSafe(c.getName());
            p.description = nullSafe(c.getDescription());
            return p;
        }
    }

    public static List<CategoryPayload> categoriesFrom(List<ClassificationCategory> cats) {
        List<CategoryPayload> out = new ArrayList<>();
        if (cats == null) return out;
        for (ClassificationCategory c : cats) {
            if (c != null) out.add(CategoryPayload.from(c));
        }
        return out;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
