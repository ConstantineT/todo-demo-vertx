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

    private Todo todo;

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

        todo = new Todo("1", "Order pizza");

        mongoClient.rxDropCollection(COLLECTION_NAME)
                .flatMap(nothing -> mongoClient.rxInsert(COLLECTION_NAME, JsonObject.mapFrom(todo)))
                .flatMap(id -> vertx.rxDeployVerticle(
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

        webClient.get("/todo/" + todo.getId()).rxSend()
                .map(HttpResponse::bodyAsJsonObject)
                .subscribe(
                        actual -> {
                            context.assertEquals(JsonObject.mapFrom(todo), actual);
                            async.complete();
                        },
                        context::fail
                );
    }

    @Test
    public void testCreateTodo_shouldReturnCorrectJsonAndCreateRecord_whenRequestWithCorrectBodySent(final TestContext context) {
        final Async async = context.async();
        final String description = "Eat pizza";

        webClient.post("/todo").rxSendJsonObject(new JsonObject().put(Todo.DESCRIPTION, description))
                .map(HttpResponse::bodyAsJsonObject)
                .flatMap(result -> {
                    context.assertNotNull(result);
                    context.assertEquals(description, result.getString(Todo.DESCRIPTION));
                    return mongoClient.rxFindOne(COLLECTION_NAME, new JsonObject().put(Todo.ID, result.getString(Todo.ID)), null);
                })
                .subscribe(
                        result -> {
                            context.assertNotNull(result);
                            context.assertEquals(description, result.getString(Todo.DESCRIPTION));
                            async.complete();
                        },
                        context::fail
                );
    }
}