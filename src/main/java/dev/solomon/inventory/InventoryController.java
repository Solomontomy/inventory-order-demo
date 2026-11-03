package dev.solomon.inventory;
import static dev.solomon.inventory.ApiModels.*;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.net.URI;
import java.util.*;
@RestController
@RequestMapping("/api")
public class InventoryController {
 private final InventoryService service;
 public InventoryController(InventoryService service) { this.service=service; }
 @GetMapping("/products") public List<Product> products(@RequestParam(defaultValue="20") @Min(1) @Max(100) int limit,
   @RequestParam(defaultValue="0") @Min(0) int offset) { return service.products(limit,offset); }
 @GetMapping("/products/{id}") public Product product(@PathVariable long id) {return service.product(id);}
 @PostMapping("/products") public ResponseEntity<Product> create(@Valid @RequestBody NewProduct body) {
  var p=service.createProduct(body);return ResponseEntity.created(URI.create("/api/products/"+p.id())).body(p);
 }
 @PostMapping("/products/{id}/restock") public Product restock(@PathVariable long id,@Valid @RequestBody Restock body) {return service.restock(id,body.quantity());}
 @PostMapping("/orders") public ResponseEntity<OrderView> place(@Valid @RequestBody NewOrder body) {
  var order=service.place(body);return ResponseEntity.created(URI.create("/api/orders/"+order.id())).body(order);
 }
 @GetMapping("/orders/{id}") public OrderView order(@PathVariable UUID id) {return service.order(id);}
 @PostMapping("/orders/{id}/cancel") public OrderView cancel(@PathVariable UUID id) {return service.cancel(id);}
}
