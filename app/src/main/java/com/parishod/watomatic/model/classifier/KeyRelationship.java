package com.parishod.watomatic.model.classifier;

public class KeyRelationship {
    private String name;
    private String role;

    public KeyRelationship() {}

    public KeyRelationship(String name, String role) {
        this.name = name;
        this.role = role;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getRole() { return role; }
    public void setRole(String role) { this.role = role; }
}
