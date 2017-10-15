package com.github.constantinet.tododemovertx.todo;

import com.github.constantinet.tododemovertx.TodoModule;
import com.google.inject.Inject;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.rxjava.core.AbstractVerticle;
import io.vertx.rxjava.core.RxHelper;
import io.vertx.rxjava.core.http.HttpServerResponse;
import io.vertx.rxjava.ext.web.Router;
import io.vertx.rxjava.ext.web.RoutingContext;
import io.vertx.rxjava.ext.web.handler.BodyHandler;
import rx.Observable;
import rx.Subscriber;

import javax.inject.Named;
import java.util.concurrent.TimeUnit;

public class TodoVerticle extends AbstractVerticle {

    private final int httpPort;
    private final TodoRepository todoRepository;

    @Inject
    public TodoVerticle(@Named(TodoModule.HTTP_PORT) final int httpPort, final TodoRepository todoRepository) {
        this.httpPort = httpPort;
        this.todoRepository = todoRepository;
    }

    @Override
    public void start(final Future<Void> future) throws Exception {
        final Router router = Router.router(vertx);
        router.route().handler(BodyHandler.create());
        router.get("/todo").handler(this::getTodos);
        router.get("/todo/:id").handler(this::getTodo);
        router.post("/todo").handler(this::createTodo);

        vertx.createHttpServer()
                .requestHandler(router::accept)
                .listen(httpPort, httpServer -> {
                    if (httpServer.succeeded()) {
                        future.complete();
                    } else {
                        future.fail(httpServer.cause());
                    }
                });
    }

    private void getTodos(final RoutingContext routingContext) {
        final HttpServerResponse response = routingContext.response()
                .putHeader("Content-Type", "application/stream+json")
                .setChunked(true);

        todoRepository.findAll()
                .zipWith(Observable.interval(1, TimeUnit.SECONDS, RxHelper.scheduler(vertx)), (todo, l) -> todo)// simulates a delay
                .subscribe(new Subscriber<Todo>() {
                    @Override
                    public void onCompleted() {
                        response.setStatusCode(200).end();
                    }

                    @Override
                    public void onError(final Throwable error) {
                        response.setStatusCode(500).end();
                    }

                    @Override
                    public void onNext(final Todo todo) {
                        response.write(JsonObject.mapFrom(todo).encode() + "\n");
                    }
                });
    }

    private void getTodo(final RoutingContext routingContext) {
        final HttpServerResponse response = routingContext.response()
                .putHeader("Content-Type", "application/json");

        final String id = routingContext.pathParam("id");
        todoRepository.findById(id).subscribe(
                todo -> response.setStatusCode(200).end(JsonObject.mapFrom(todo).encode()),
                error -> response.setStatusCode(500).end()
        );
    }

    private void createTodo(final RoutingContext routingContext) {
        final HttpServerResponse response = routingContext.response()
                .putHeader("Content-Type", "application/json");

        final Todo todoToCreate = routingContext.getBodyAsJson().mapTo(Todo.class);
        todoRepository.insert(todoToCreate).subscribe(
                todo -> response.setStatusCode(200).end(JsonObject.mapFrom(todo).encode()),
                error -> response.setStatusCode(500).end()
        );
    }
}