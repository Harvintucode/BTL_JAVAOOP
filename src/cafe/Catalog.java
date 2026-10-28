package cafe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thực đơn/định mức nguyên liệu - chỉnh sửa tập trung tại đây. */
public final class Catalog {
    private Catalog() {}
    public static final List<Data.Product> PRODUCTS = List.of(
        p("americano", "Americano", "Cà phê", 25000, "americano.jpg",
          "Cà phê (g)", 18, "Đá (g)", 100),
        p("tuna", "Tuna Mayo", "Đồ ăn", 49000, "tuna.jpg",
          "Bánh mì (cái)", 1, "Cá ngừ (g)", 70, "Rau (g)", 30),
        p("matcha", "Matcha Latte", "Trà", 39000, "matcha.jpg",
          "Matcha (g)", 12, "Sữa (ml)", 160, "Đá (g)", 100),
        p("meatball", "Fried Meatball", "Đồ ăn", 45000, "meatball.jpg",
          "Thịt viên (viên)", 5, "Rau (g)", 40),
        p("lemon", "Lemon Tea", "Trà", 29000, "lemon.jpg",
          "Trà (g)", 10, "Chanh (quả)", 1, "Siro (ml)", 20, "Đá (g)", 120),
        p("soda", "Happy Soda", "Soda", 35000, "soda.jpg",
          "Soda (ml)", 250, "Siro (ml)", 30, "Đá (g)", 130)
    );
    public static Data.Product find(String id) {
        return PRODUCTS.stream().filter(p -> p.id().equals(id)).findFirst()
               .orElseThrow(() -> new IllegalArgumentException("Không tìm thấy món: " + id));
    }
    public static final List<String> CATEGORIES = List.of("Tất cả", "Cà phê", "Trà", "Đồ ăn", "Soda");

    private static Data.Product p(String id, String name, String category, long price,
                                  String image, Object... ingredients) {
        Map<String, Integer> recipe = new LinkedHashMap<>();
        for (int i = 0; i < ingredients.length; i += 2)
            recipe.put((String) ingredients[i], (Integer) ingredients[i + 1]);
        return new Data.Product(id, name, category, price, image, Map.copyOf(recipe));
    }
}
