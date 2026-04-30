package com.chuwa.shopping.item.config;

import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.item.entity.InventoryDocument;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.dto.item.ItemStatus;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Configuration
public class ItemSeedConfig {

    private static final java.util.Map<String, String> CATEGORY_MIGRATION = java.util.Map.ofEntries(
            java.util.Map.entry("Kitchen",  "Home & Garden"),
            java.util.Map.entry("Home",     "Home & Garden"),
            java.util.Map.entry("Outdoor",  "Sports & Outdoors"),
            java.util.Map.entry("Grocery",  "Grocery & Essentials"),
            java.util.Map.entry("Health",   "Health & Wellness"),
            java.util.Map.entry("Apparel",  "Clothing & Shoes"),
            java.util.Map.entry("Tools",    "Home & Garden"),
            java.util.Map.entry("Kids",     "Toys & Games"),
            java.util.Map.entry("Toys",     "Toys & Games"),
            java.util.Map.entry("Games",    "Toys & Games")
    );

    @Bean
    public CommandLineRunner seedItems(ItemRepository itemRepository) {
        return args -> {
            // Migrate old category names to new department names
            itemRepository.findAll().forEach(item -> {
                String migrated = CATEGORY_MIGRATION.get(item.getCategory());
                if (migrated != null) {
                    item.setCategory(migrated);
                    itemRepository.save(item);
                }
            });

            if (itemRepository.count() > 0) {
                return;
            }
            itemRepository.saveAll(List.of(
                    item("SKU-1001", "123456789012", "Mainstays Ceramic Coffee Mug", "Mainstays", "Home & Garden", "White ceramic mug for coffee and tea.", "12.99", "12.99", 0, "https://images.unsplash.com/photo-1577937927133-66ef06acdf18?auto=format&fit=crop&w=900&q=80", Map.of("color", "White"), 90),
                    item("SKU-1002", "123456789013", "onn. Bluetooth Headphones", "onn.", "Electronics", "Over-ear wireless headphones with all-day playback.", "39.00", "49.00", 20, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80", Map.of("color", "Black"), 36),
                    item("SKU-1003", "123456789014", "Better Homes & Gardens Throw Pillow", "Better Homes & Gardens", "Home & Garden", "Textured accent pillow for sofa or guest room.", "18.50", "18.50", 0, "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80", Map.of("color", "Rust"), 55),
                    item("SKU-1004", "123456789015", "Ozark Trail Folding Camp Chair", "Ozark Trail", "Sports & Outdoors", "Portable folding chair for camping and outdoor activities.", "24.88", "34.88", 29, "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80", Map.of("color", "Blue"), 28),
                    item("SKU-1005", "123456789016", "Great Value Sparkling Water 12-Pack", "Great Value", "Grocery & Essentials", "Lime flavored sparkling water multipack.", "4.98", "4.98", 0, "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=900&q=80", Map.of("flavor", "Lime"), 140),
                    item("SKU-1006", "123456789017", "Equate Daily Vitamin Gummies", "Equate", "Health & Wellness", "Berry gummy vitamins for everyday wellness support.", "10.47", "10.47", 0, "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=900&q=80", Map.of("type", "Gummies"), 72),
                    item("SKU-1007", "123456789018", "George Men's Crew T-Shirt 3-Pack", "George", "Clothing & Shoes", "Soft crewneck t-shirts for everyday wear.", "14.96", "14.96", 0, "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80", Map.of("fit", "Regular"), 64),
                    item("SKU-1008", "123456789019", "Hyper Tough 20V Drill Kit", "Hyper Tough", "Home & Garden", "Cordless drill kit with charger and bit set. Great for home improvement projects.", "49.97", "69.97", 29, "https://images.unsplash.com/photo-1504148455328-c376907d081c?auto=format&fit=crop&w=900&q=80", Map.of("battery", "20V"), 18),
                    item("SKU-1009", "123456789020", "Little Tikes Classic Toy Chest", "Little Tikes", "Toys & Games", "Kids toy storage chest, perfect for organizing toys and games.", "39.99", "49.99", 20, "https://images.unsplash.com/photo-1559827260-dc66d52bef19?auto=format&fit=crop&w=900&q=80", Map.of("age", "2+"), 52),
                    item("SKU-1010", "123456789021", "Huggies Newborn Diapers Size 1", "Huggies", "Baby", "Soft newborn diapers with wetness indicator, pack of 84.", "24.97", "24.97", 0, "https://images.unsplash.com/photo-1604068549290-dea0e4a305ca?auto=format&fit=crop&w=900&q=80", Map.of("size", "Newborn"), 120),
                    item("SKU-1011", "123456789022", "EvoShield Youth Baseball Helmet", "EvoShield", "Sports & Outdoors", "Youth batting helmet for baseball and softball.", "34.99", "44.99", 22, "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=900&q=80", Map.of("sport", "Baseball"), 30),
                    item("SKU-1012", "123456789023", "Equate Beauty Moisturizing Face Wash", "Equate Beauty", "Beauty", "Gentle daily face wash for all skin types.", "6.97", "6.97", 0, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80", Map.of("skin", "All types"), 85)
            ));
        };
    }

    private ItemDocument item(String sku, String upc, String itemName, String brand, String category,
                              String description, String unitPrice, String listPrice, Integer discountPercent,
                              String imageUrl, Map<String, String> attributes, int availableQuantity) {
        ItemDocument item = new ItemDocument();
        item.setSku(sku);
        item.setUpc(upc);
        item.setItemName(itemName);
        item.setBrand(brand);
        item.setCategory(category);
        item.setDescription(description);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setListPrice(new BigDecimal(listPrice));
        item.setDiscountPercent(discountPercent);
        item.setCurrencyCode("USD");
        item.setPictureUrls(List.of(imageUrl));
        item.setStatus(ItemStatus.ACTIVE);
        item.setAttributes(attributes);
        item.setCreatedAt(LocalDateTime.now());
        item.setUpdatedAt(LocalDateTime.now());

        InventoryDocument inventory = new InventoryDocument();
        inventory.setTotalQuantity(availableQuantity + 10);
        inventory.setAvailableQuantity(availableQuantity);
        inventory.setReservedQuantity(10);
        inventory.setReorderLevel(5);
        inventory.setWarehouseCode("WH1");
        inventory.setInStock(availableQuantity > 0);
        item.setInventory(inventory);

        return item;
    }
}
