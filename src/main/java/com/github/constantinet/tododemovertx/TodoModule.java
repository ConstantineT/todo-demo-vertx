package com.github.constantinet.tododemovertx;

import com.github.constantinet.tododemovertx.todo.TodoRepository;
import com.google.inject.AbstractModule;
import com.google.inject.ConfigurationException;
import com.google.inject.name.Names;
import com.google.inject.spi.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.Context;
import io.vertx.reactivex.core.Vertx;
import io.vertx.reactivex.ext.mongo.MongoClient;

import java.util.Collections;

public class TodoModule extends AbstractModule {

    public static final String HTTP_PORT = "httpPort";

    private final Context context;

    public TodoModule() {
        context = Vertx.currentContext();
        if (context == null) {
            throw new ConfigurationException(Collections.singleton(new Message("Must be executed within Vert.x context")));
        }
    }

    @Override
    protected void configure() {
        final JsonObject mongoConfig = context.config().getJsonObject("mongoConfig");
        final int httpPort = context.config().getInteger("httpPort", 8080);

        bindConstant().annotatedWith(Names.named(HTTP_PORT)).to(httpPort);
        bind(MongoClient.class).toInstance(MongoClient.createShared(context.owner(), mongoConfig));
        bind(TodoRepository.class);
    }
}