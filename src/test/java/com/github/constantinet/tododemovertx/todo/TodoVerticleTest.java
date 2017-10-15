package com.github.constantinet.tododemovertx.todo;

import com.github.constantinet.tododemovertx.TodoModule;
import de.flapdoodle.embed.mongo.MongodProcess;
import de.flapdoodle.embed.mongo.MongodStarter;
import de.flapdoodle.embed.mongo.config.IMongodConfig;
import de.flapdoodle.embed.mongo.config.MongodConfigBuilder;
import de.flapdoodle.embed.mongo.config.Net;
import de.flapdoodle.embed.mongo.distribution.Version;
import de.flapdoodle.embed.process.runtime.Network;
import io.vertx.core.DeploymentOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.unit.Async;
import io.vertx.ext.unit.TestContext;
import io.vertx.ext.unit.junit.RunTestOnContext;
import io.vertx.ext.unit.junit.VertxUnitRunner;
import io.vertx.ext.web.client.WebClientOptions;
import io.vertx.rxjava.core.Vertx;
import io.vertx.rxjava.ext.mongo.MongoClient;
import io.vertx.rxjava.ext.web.client.HttpResponse;
import io.vertx.rxjava.ext.web.client.WebClient;
import org.junit.*;
import org.junit.runner.RunWith;
import rx.Single;

import java.io.IOException;
import java.util.concurrent.ExecutionException;

@RunWith(VertxUnitRunner.class)
public class TodoVerticleTest {

    private static final String HOST = "localhost";
    private static final int MONGO_PORT = 12345;
    private static final int HTTP_PORT = 12346;
    private static final String MONGO_DB_NAME = "test";
    private static final String COLLECTION_NAME = "todos";

    private static JsonObject mongoConfig;
    private static MongodProcess mongoProcess;

    @Rule
    public RunTestOnContext runTestOnContextRule = new RunTestOnContext();

    private MongoClient mongoClient;
    private WebClient webClient;

    private Todo todo1;
    private Todo todo2;

    @BeforeClass
    public static void setUpInfrastructure() throws IOException {
        mongoConfig = new JsonObject().put("host", HOST).put("port", MONGO_PORT).put("db_name", MONGO_DB_NAME);

        final IMongodConfig mongodConfig = new MongodConfigBuilder()
                .version(Version.Main.PRODUCTION)
                .net(new Net(MONGO_PORT, Network.localhostIsIPv6()))
                .build();
        mongoProcess = MongodStarter.getDefaultInstance().prepare(mongodConfig).start();
    }

    @AfterClass
    public static void tearDownInfrastructure() {
        mongoProcess.stop();
    }

    @Before
    public void setUp(TestContext context) throws ExecutionException, InterruptedException {
        final Async async = context.async();

        final Vertx vertx = Vertx.newInstance(runTestOnContextRule.vertx());

        mongoClient = MongoClient.createShared(Vertx.newInstance(runTestOnContextRule.vertx()), mongoConfig);

        webClient = WebClient.create(vertx,
                new WebClientOptions()
                        .setDefaultPort(HTTP_PORT)
                        .setDefaultHost(HOST));

        todo1 = new Todo("1", "Order pizza");
        todo2 = new Todo("2", "Eat pizza");

        mongoClient.rxDropCollection(COLLECTION_NAME)
                .flatMap(nothing -> Single.zip(
                        mongoClient.rxInsert(COLLECTION_NAME, JsonObject.mapFrom(todo1)),
                        mongoClient.rxInsert(COLLECTION_NAME, JsonObject.mapFrom(todo2)),
                        (id1, id2) -> (Void) null))
                .flatMap(nothing -> vertx.rxDeployVerticle(
                        "java-guice:com.github.constantinet.tododemovertx.todo.TodoVerticle",
                        new DeploymentOptions()
                                .setConfig(new JsonObject()
                                        .put("mongoConfig", mongoConfig)
                                        .put("httpPort", HTTP_PORT)
                                        .put("guice_binder", TodoModule.class.getName()))
                ))
                .subscribe(
                        result -> async.complete(),
                        context::fail
                );
    }

    @Test
    public void testGetTodo_shouldReturnCorrectJson_whenRequestWithExistingIdSent(final TestContext context) {
        final Async async = context.async();

        webClient.get("/todo/" + todo2.getId()).rxSend()
                .doOnSuccess(response -> context.assertEquals(200, response.statusCode()))
                .map(HttpResponse::bodyAsJsonObject)
                .doOnSuccess(jsonObject -> context.assertEquals(JsonObject.mapFrom(todo2), jsonObject))
                .subscribe(result -> async.complete(), context::fail);
    }

    @Test
    public void testCreateTodo_shouldReturnCorrectJsonAndCreateRecord_whenRequestWithCorrectBodySent(final TestContext context) {
        final Async async = context.async();
        final String description = "Take a nap";

        webClient.post("/todo").rxSendJsonObject(new JsonObject().put(Todo.DESCRIPTION, description))
                .doOnSuccess(response -> context.assertEquals(200, response.statusCode()))
                .map(HttpResponse::bodyAsJsonObject)
                .doOnSuccess(jsonObject -> {
                    context.assertNotNull(jsonObject);
                    context.assertEquals(description, jsonObject.getString(Todo.DESCRIPTION));
                })
                .flatMap(result -> mongoClient.rxFindOne(COLLECTION_NAME, new JsonObject().put(Todo.ID, result.getString(Todo.ID)), null))
                .doOnSuccess(jsonObject -> {
                    context.assertNotNull(jsonObject);
                    context.assertEquals(description, jsonObject.getString(Todo.DESCRIPTION));
                })
                .subscribe(result -> async.complete(), context::fail);
    }

    @Test
    public void testDeleteTodo_shouldDeleteRecord_whenRequestWithExistingIdSent(final TestContext context) {
        final Async async = context.async();

        webClient.delete("/todo/" + todo1.getId()).rxSend()
                .doOnSuccess(response -> context.assertEquals(200, response.statusCode()))
                .flatMap(response -> mongoClient.rxFind(COLLECTION_NAME, new JsonObject()))
                .doOnSuccess(jsonObjects -> {
                    context.assertNotNull(jsonObjects);
                    context.assertEquals(1, jsonObjects.size());
                    context.assertEquals(JsonObject.mapFrom(todo2), jsonObjects.get(0));
                })
                .subscribe(result -> async.complete(), context::fail);
    }
}