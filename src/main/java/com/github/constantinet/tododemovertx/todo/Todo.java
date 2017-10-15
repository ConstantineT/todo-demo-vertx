package com.github.constantinet.tododemovertx.todo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

public final class Todo {

    public static final String ID = "_id";
    public static final String DESCRIPTION = "description";

    @JsonProperty(ID)
    private String id;

    @JsonProperty(DESCRIPTION)
    private String description;

    @JsonCreator
    public Todo(@JsonProperty(ID) final String id, @JsonProperty(DESCRIPTION) final String description) {
        this.id = id;
        this.description = description;
    }

    public String getId() {
        return id;
    }

    public String getDescription() {
        return description;
    }
}