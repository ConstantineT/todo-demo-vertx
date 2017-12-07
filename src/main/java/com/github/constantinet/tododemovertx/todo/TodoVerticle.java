package com.github.constantinet.tododemovertx.todo;

import com.github.constantinet.tododemovertx.TodoModule;
import com.google.inject.Inject;
import io.reactivex.Observable;
import io.reactivex.observers.DefaultObserver;
import io.vertx.core.Future;
import io.vertx.core.json.JsonObject;
import io.vertx.reactivex.core.AbstractVerticle;
import io.vertx.reactivex.core.RxHelper;
import io.vertx.reactivex.core.http.HttpServerResponse;
import io.vertx.reactivex.ext.web.Router;
import io.vertx.reactivex.ext.web.RoutingContext;
import io.vertx.reactivex.ext.web.handler.BodyHandler;

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
        router.delete().path("/todo/:id").handler(this::deleteTodo);

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
                .subscribe(new DefaultObserver<Todo>() {

                    @Override
                    public void onNext(final Todo todo) {
                        response.write(JsonObject.mapFrom(todo).encode() + "\n");
                    }

                    @Override
                    public void onComplete() {
                        response.setStatusCode(200).end();
                    }

                    @Override
                    public void onError(final Throwable error) {
                        response.setStatusCode(500).end();
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

    private void deleteTodo(final RoutingContext routingContext) {
        final HttpServerResponse response = routingContext.response();

        final String id = routingContext.pathParam("id");
        todoRepository.delete(id).subscribe(
                () -> response.setStatusCode(200).end(),
                error -> response.setStatusCode(500).end()
        );
    }
}