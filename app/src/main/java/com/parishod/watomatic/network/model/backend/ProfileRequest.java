package com.parishod.watomatic.network.model.backend;

import com.google.gson.annotations.SerializedName;

public class ProfileRequest {
    @SerializedName("user_id") public String userId;
    @SerializedName("profile") public ClassifyRequest.ProfilePayload profile;
}
