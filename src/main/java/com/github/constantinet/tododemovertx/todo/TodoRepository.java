package com.github.constantinet.tododemovertx.todo;

import com.google.inject.Inject;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.ext.mongo.MongoClient;
import rx.Observable;
import rx.Single;

public class TodoRepository {

    private final MongoClient mongoClient;

    @Inject
    public TodoRepository(final MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public Observable<Todo> findAll() {
        return mongoClient
                .rxFind("todos", new JsonObject())
                .flatMapObservable(Observable::from)
                .map(todo -> todo.mapTo(Todo.class));
    }

    public Single<Todo> findById(final String id) {
        final JsonObject query = new JsonObject().put(Todo.ID, id);

        return mongoClient
                .rxFindOne("todos", query, new JsonObject())
                .map(todo -> todo != null ? todo.mapTo(Todo.class) : null);
    }

    public Single<Todo> insert(final Todo todo) {
        final JsonObject todoJsonObject = JsonObject.mapFrom(todo);
        todoJsonObject.remove(Todo.ID);

        return mongoClient
                .rxInsert("todos", todoJsonObject)
                .map(id -> new Todo(id, todo.getDescription()));
    }

    public Single<Void> delete(final String id) {
        final JsonObject query = new JsonObject().put(Todo.ID, id);

        return mongoClient
                .rxRemove("todos", query);
    }
}