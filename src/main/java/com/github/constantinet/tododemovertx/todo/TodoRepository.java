package com.github.constantinet.tododemovertx.todo;

import com.google.inject.Inject;
import io.reactivex.Completable;
import io.reactivex.Observable;
import io.reactivex.Single;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.ext.mongo.MongoClient;

public class TodoRepository {

    public static final String COLLECTION_NAME = "todos";

    private final MongoClient mongoClient;

    @Inject
    public TodoRepository(final MongoClient mongoClient) {
        this.mongoClient = mongoClient;
    }

    public Observable<Todo> findAll() {
        return mongoClient
                .rxFind(COLLECTION_NAME, new JsonObject())
                .flatMapObservable(Observable::fromIterable)
                .map(todo -> todo.mapTo(Todo.class));
    }

    public Single<Todo> findById(final String id) {
        final JsonObject query = new JsonObject().put(Todo.ID, id);

        return mongoClient
                .rxFindOne(COLLECTION_NAME, query, new JsonObject())
                .map(todo -> todo != null ? todo.mapTo(Todo.class) : null);
    }

    public Single<Todo> insert(final Todo todo) {
        final JsonObject todoJsonObject = JsonObject.mapFrom(todo);
        todoJsonObject.remove(Todo.ID);

        return mongoClient
                .rxInsert(COLLECTION_NAME, todoJsonObject)
                .map(id -> new Todo(id, todo.getDescription()));
    }

    public Completable delete(final String id) {
        final JsonObject query = new JsonObject().put(Todo.ID, id);

        return mongoClient
                .rxRemove(COLLECTION_NAME, query);
    }
}