package shopping.cart;

import akka.actor.typed.ActorSystem;
import akka.actor.typed.javadsl.Behaviors;
import akka.grpc.GrpcClientSettings;
import akka.http.javadsl.model.HttpRequest;
import akka.http.javadsl.model.HttpResponse;
import akka.japi.function.Function;
import akka.management.cluster.bootstrap.ClusterBootstrap;
import akka.management.javadsl.AkkaManagement;
import com.typesafe.config.Config;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import shopping.cart.proto.ShoppingCartService;
import shopping.cart.repository.ItemPopularityRepository;
import shopping.cart.repository.ItemPopularityRepositoryImpl;
// tag::SendOrderProjection[]
import shopping.order.proto.ShoppingOrderService;
import shopping.order.proto.ShoppingOrderServiceClient;

import java.util.concurrent.CompletionStage;

public class Main {
  // end::SendOrderProjection[]

  private static final Logger logger = LoggerFactory.getLogger(Main.class);

  // tag::SendOrderProjection[]
  public static void main(String[] args) {
    ActorSystem<Void> system = ActorSystem.create(Behaviors.empty(), "shopping-cart-service");
    try {
      init(system, orderServiceClient(system));
//      init(system);
    } catch (Exception e) {
      logger.error("Terminating due to initialization failure.", e);
      system.terminate();
    }
  }

  public static void init(ActorSystem<Void> system, ShoppingOrderService orderService) {
//    public static void init(ActorSystem<Void> system) {
    // end::SendOrderProjection[]
    AkkaManagement.get(system).start();
    ClusterBootstrap.get(system).start();

    ShoppingCart.init(system);
    ItemPopularityRepository itemPopularityRepository = new ItemPopularityRepositoryImpl();
    ItemPopularityProjection.init(system, itemPopularityRepository);

//    Function<HttpRequest, CompletionStage<HttpResponse>> eventProducerService = PublishEventsGrpc.eventProducerService(system);

    // tag::SendOrderProjection[]
//    SendOrderProjection.init(system, orderService); // <1>
    // end::SendOrderProjection[]

    Config config = system.settings().config();
    String grpcInterface = config.getString("shopping-cart-service.grpc.interface");
    int grpcPort = config.getInt("shopping-cart-service.grpc.port");
    ShoppingCartService grpcService = new ShoppingCartServiceImpl(system, itemPopularityRepository);
//    ShoppingCartServer.start(grpcInterface, grpcPort, system, grpcService, eventProducerService);
      ShoppingCartServer.start(grpcInterface, grpcPort, system, grpcService);

  }

  static ShoppingOrderService orderServiceClient(ActorSystem<?> system) { // <2>
    GrpcClientSettings orderServiceClientSettings =
        GrpcClientSettings.connectToServiceAt(
                system.settings().config().getString("shopping-order-service.host"),
                system.settings().config().getInt("shopping-order-service.port"),
                system)
            .withTls(false);

    return ShoppingOrderServiceClient.create(orderServiceClientSettings, system);
  }


}
