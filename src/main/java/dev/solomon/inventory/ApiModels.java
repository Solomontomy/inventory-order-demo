package dev.solomon.inventory;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
public final class ApiModels {
 private ApiModels() {}
 public record Product(long id,String sku,String name,BigDecimal price,int stock) {}
 public record NewProduct(@NotBlank @Pattern(regexp="[A-Z0-9-]{3,40}") String sku,
   @NotBlank @Size(max=120) String name, @NotNull @DecimalMin("0.01") @Digits(integer=10,fraction=2) BigDecimal price,
   @Min(0) @Max(1000000) int stock) {}
 public record Restock(@Min(1) @Max(1000000) int quantity) {}
 public record LineRequest(@Positive long productId,@Min(1) @Max(1000000) int quantity) {}
 public record NewOrder(@NotBlank @Pattern(regexp="[A-Za-z0-9-]{3,80}") String reference,
   @NotEmpty @Size(max=50) List<@NotNull @Valid LineRequest> lines) {}
 public record OrderLine(long productId,int quantity,BigDecimal unitPrice) {}
 public record OrderView(UUID id,String reference,String status,List<OrderLine> lines,BigDecimal total) {}
}
