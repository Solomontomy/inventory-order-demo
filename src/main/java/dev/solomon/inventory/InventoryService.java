package dev.solomon.inventory;
import static dev.solomon.inventory.ApiModels.*;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import java.math.BigDecimal;
import java.util.*;

@Service
public class InventoryService {
 private final JdbcClient db;
 public InventoryService(JdbcClient db) { this.db=db; }
 public List<Product> products(int limit,int offset) {
  return db.sql("SELECT * FROM products ORDER BY id LIMIT :limit OFFSET :offset")
    .param("limit",limit).param("offset",offset).query(Product.class).list();
 }
 public Product product(long id) {
  return db.sql("SELECT * FROM products WHERE id=:id").param("id",id).query(Product.class)
    .optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Product not found"));
 }
 @Transactional public Product createProduct(NewProduct p) {
  return db.sql("INSERT INTO products(sku,name,price,stock) VALUES(:sku,:name,:price,:stock) RETURNING *")
    .param("sku",p.sku()).param("name",p.name().trim()).param("price",p.price()).param("stock",p.stock()).query(Product.class).single();
 }
 @Transactional public Product restock(long id,int quantity) {
  // Atomic arithmetic prevents lost updates; the bound prevents integer overflow.
  return db.sql("UPDATE products SET stock=stock+:quantity WHERE id=:id AND stock<=1000000000-:quantity RETURNING *")
    .param("id",id).param("quantity",quantity).query(Product.class).optional().orElseThrow(()->{
     product(id);return new ResponseStatusException(HttpStatus.CONFLICT,"Stock limit exceeded");
    });
 }
 @Transactional public OrderView place(NewOrder request) {
  var lines=request.lines().stream().sorted(Comparator.comparingLong(LineRequest::productId)).toList();
  if(lines.stream().map(LineRequest::productId).distinct().count()!=lines.size())
   throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Duplicate product lines are not allowed");
  UUID id=UUID.randomUUID();
  db.sql("INSERT INTO orders(id,reference,status) VALUES(:id,:reference,'CONFIRMED')")
   .param("id",id).param("reference",request.reference()).update();
  for(var line:lines) {
   // Conditional UPDATE acquires a row lock. Sorted IDs keep lock order consistent.
   var price=db.sql("UPDATE products SET stock=stock-:qty WHERE id=:id AND stock>=:qty RETURNING price")
    .param("qty",line.quantity()).param("id",line.productId()).query(BigDecimal.class).optional();
   if(price.isEmpty()) {
    product(line.productId());
    throw new ResponseStatusException(HttpStatus.CONFLICT,"Insufficient stock for product "+line.productId());
   }
   db.sql("INSERT INTO order_lines(order_id,product_id,quantity,unit_price) VALUES(:order,:product,:qty,:price)")
    .param("order",id).param("product",line.productId()).param("qty",line.quantity()).param("price",price.get()).update();
  }
  return order(id);
 }
 public OrderView order(UUID id) {
  var header=db.sql("SELECT reference,status FROM orders WHERE id=:id").param("id",id)
   .query((rs,n)->new String[]{rs.getString(1),rs.getString(2)}).optional()
   .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
  var lines=db.sql("SELECT product_id,quantity,unit_price FROM order_lines WHERE order_id=:id ORDER BY product_id")
   .param("id",id).query(OrderLine.class).list();
  var total=lines.stream().map(l->l.unitPrice().multiply(BigDecimal.valueOf(l.quantity()))).reduce(BigDecimal.ZERO,BigDecimal::add);
  return new OrderView(id,header[0],header[1],lines,total);
 }
 @Transactional public OrderView cancel(UUID id) {
  var status=db.sql("SELECT status FROM orders WHERE id=:id FOR UPDATE").param("id",id).query(String.class)
   .optional().orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Order not found"));
  if(status.equals("CANCELLED")) return order(id);
  for(var line:order(id).lines()) {
   int updated=db.sql("UPDATE products SET stock=stock+:qty WHERE id=:id AND stock<=1000000000-:qty")
    .param("qty",line.quantity()).param("id",line.productId()).update();
   if(updated!=1) throw new ResponseStatusException(HttpStatus.CONFLICT,"Stock limit exceeded");
  }
  db.sql("UPDATE orders SET status='CANCELLED' WHERE id=:id").param("id",id).update();
  return order(id);
 }
}
