package dev.solomon.inventory;
import static org.assertj.core.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
import static dev.solomon.inventory.ApiModels.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;

@SpringBootTest(properties={
 "spring.datasource.url=${TEST_DB_URL:jdbc:postgresql://localhost:5433/inventory_demo_test}",
 "spring.datasource.username=${TEST_DB_USER:demo}","spring.datasource.password=${TEST_DB_PASSWORD:demo_local_only}"})
@AutoConfigureMockMvc
class InventoryIntegrationTest {
 @Autowired InventoryService service;
 @Autowired JdbcTemplate db;
 @Autowired MockMvc mvc;
 @BeforeEach void reset() {
  String database=db.queryForObject("SELECT current_database()",String.class);
  if(database==null || !database.endsWith("_test")) throw new IllegalStateException("Tests require a dedicated database ending in _test");
  db.execute("TRUNCATE order_lines,orders,products RESTART IDENTITY CASCADE");
 }
 Product product(int stock) {return service.createProduct(new NewProduct("DEMO-"+UUID.randomUUID().toString().substring(0,8).toUpperCase(),"Sample",new BigDecimal("12.50"),stock));}
 NewOrder order(String ref,long id,int qty) {return new NewOrder(ref,List.of(new LineRequest(id,qty)));}
 @Test void placesOrderAndSnapshotsPrice() {
  var p=product(10);var o=service.place(order("DEMO-001",p.id(),3));
  assertThat(o.total()).isEqualByComparingTo("37.50");assertThat(service.product(p.id()).stock()).isEqualTo(7);
  db.update("UPDATE products SET price=99 WHERE id=?",p.id());
  assertThat(service.order(o.id()).total()).isEqualByComparingTo("37.50");
 }
 @Test void failedSecondLineRollsBackFirstLineAndOrder() {
  var a=product(10);var b=product(0);
  assertThatThrownBy(()->service.place(new NewOrder("DEMO-FAIL",List.of(new LineRequest(a.id(),2),new LineRequest(b.id(),1)))))
   .isInstanceOf(ResponseStatusException.class);
  assertThat(service.product(a.id()).stock()).isEqualTo(10);
  assertThat(db.queryForObject("SELECT count(*) FROM orders",Integer.class)).isZero();
 }
 @Test void cancellationRestoresStockExactlyOnce() {
  var p=product(10);var o=service.place(order("DEMO-CANCEL",p.id(),4));
  service.cancel(o.id());service.cancel(o.id());
  assertThat(service.product(p.id()).stock()).isEqualTo(10);assertThat(service.order(o.id()).status()).isEqualTo("CANCELLED");
 }
 @Test void duplicateReferenceCannotDeductStockAgain() {
  var p=product(10);service.place(order("DEMO-DUP",p.id(),2));
  assertThatThrownBy(()->service.place(order("DEMO-DUP",p.id(),2))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
  assertThat(service.product(p.id()).stock()).isEqualTo(8);
 }
 @Test void concurrentOrdersCannotOversell() throws Exception {
  var p=product(1);var start=new CountDownLatch(1);
  try(var pool=Executors.newFixedThreadPool(2)) {
   List<Future<Boolean>> futures=new ArrayList<>();
   for(int i=0;i<2;i++) {final int n=i;futures.add(pool.submit(()->{start.await();try {service.place(order("RACE-"+n,p.id(),1));return true;}catch(ResponseStatusException e){assertThat(e.getStatusCode().value()).isEqualTo(409);return false;}}));}
   start.countDown();int successes=0;for(var f:futures) if(f.get(10,TimeUnit.SECONDS)) successes++;
   assertThat(successes).isEqualTo(1);assertThat(service.product(p.id()).stock()).isZero();
  }
 }
 @Test void rejectsDuplicateProductLines() {
  var p=product(10);
  assertThatThrownBy(()->service.place(new NewOrder("DEMO-LINES",List.of(new LineRequest(p.id(),1),new LineRequest(p.id(),2)))))
   .isInstanceOf(ResponseStatusException.class);
  assertThat(service.product(p.id()).stock()).isEqualTo(10);
 }
 @Test void invalidRequestsReturnProblemDetails() throws Exception {
  mvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{\"sku\":\"x\",\"name\":\"\",\"price\":-1,\"stock\":-2}"))
   .andExpect(status().isBadRequest()).andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON));
  mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"DEMO-NULL\",\"lines\":[null]}"))
   .andExpect(status().isBadRequest());
  mvc.perform(get("/api/products?limit=101")).andExpect(status().isBadRequest());
 }
 @Test void httpLifecycleAndConflict() throws Exception {
  var p=product(2);
  mvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{\"reference\":\"HTTP-001\",\"lines\":[{\"productId\":"+p.id()+",\"quantity\":3}]}"))
   .andExpect(status().isConflict()).andExpect(jsonPath("detail").value("Insufficient stock for product "+p.id()));
  mvc.perform(get("/api/products/99999")).andExpect(status().isNotFound());
  mvc.perform(post("/api/products/"+p.id()+"/restock").contentType(MediaType.APPLICATION_JSON).content("{\"quantity\":5}"))
   .andExpect(status().isOk()).andExpect(jsonPath("stock").value(7));
 }
}
