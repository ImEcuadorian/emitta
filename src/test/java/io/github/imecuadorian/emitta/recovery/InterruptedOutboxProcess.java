package io.github.imecuadorian.emitta.recovery;

/** Isolated child killed after Rabbit confirms and before the durable published marker. */
public final class InterruptedOutboxProcess {
    public static void main(String[] args) throws Exception {
        String url=System.getenv("SYNTHETIC_DB_URL"),host=System.getenv("SYNTHETIC_RABBIT_HOST");
        if(url==null || !(url.startsWith("jdbc:postgresql://localhost:") || url.startsWith("jdbc:postgresql://127.0.0.1:"))
            || !("localhost".equals(host) || "127.0.0.1".equals(host)))throw new IllegalArgumentException("Isolated loopback services required");
        var source=new org.springframework.jdbc.datasource.DriverManagerDataSource(url,System.getenv("SYNTHETIC_DB_USER"),System.getenv("SYNTHETIC_DB_PASSWORD"));
        var claims=new io.github.imecuadorian.emitta.outbox.adapter.out.persistence.PostgreSqlOutboxClaimAdapter(new org.springframework.jdbc.core.JdbcTemplate(source));
        var factory=new org.springframework.amqp.rabbit.connection.CachingConnectionFactory(host,Integer.parseInt(System.getenv("SYNTHETIC_RABBIT_PORT")));
        factory.setUsername(System.getenv("SYNTHETIC_RABBIT_USER"));factory.setPassword(System.getenv("SYNTHETIC_RABBIT_PASSWORD"));
        factory.setPublisherConfirmType(org.springframework.amqp.rabbit.connection.CachingConnectionFactory.ConfirmType.CORRELATED);
        factory.setPublisherReturns(true);
        var publisher=new io.github.imecuadorian.emitta.outbox.adapter.out.messaging.RabbitOutboxMessagePublisher(new org.springframework.amqp.rabbit.core.RabbitTemplate(factory));
        var events=claims.claimBatch("synthetic-crash-child",50,java.time.Instant.now().minusSeconds(120));
        if(events.size()!=2 || !publisher.publishBatch(events).isEmpty())throw new IllegalStateException("Synthetic batch not confirmed");
        System.out.println("CONFIRMED_UNMARKED");System.out.flush();Thread.sleep(java.time.Duration.ofMinutes(1));
        factory.destroy();
    }
}
