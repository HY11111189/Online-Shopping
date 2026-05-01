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

            ensureItem(itemRepository,
                    item("SKU-1005A", "123456789024", "Fresh Strawberries 1 lb", "Great Value", "Fruits & Produce", "Sweet fresh strawberries, a classic fruit snack for breakfast or desserts.", "3.98", "4.98", 20, "https://images.unsplash.com/photo-1464965911861-746a04b4bca6?auto=format&fit=crop&w=900&q=80", Map.of("type", "Fruit"), 96));

            List.of(
                    item("SKU-1001", "123456789012", "Mainstays Ceramic Coffee Mug", "Mainstays", "Home & Garden", "White ceramic mug for coffee and tea.", "12.99", "12.99", 0, "https://images.unsplash.com/photo-1577937927133-66ef06acdf18?auto=format&fit=crop&w=900&q=80", Map.of("color", "White"), 90),
                    item("SKU-1002", "123456789013", "onn. Bluetooth Headphones", "onn.", "Electronics", "Over-ear wireless headphones with all-day playback.", "39.00", "49.00", 20, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80", Map.of("color", "Black"), 36),
                    item("SKU-1003", "123456789014", "Better Homes & Gardens Throw Pillow", "Better Homes & Gardens", "Home & Garden", "Textured bedding accent pillow for sofa, guest room, or bedroom decor.", "18.50", "18.50", 0, "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80", Map.of("color", "Rust"), 55),
                    item("SKU-1004", "123456789015", "Ozark Trail Folding Camp Chair", "Ozark Trail", "Sports & Outdoors", "Portable folding chair for camping and outdoor activities.", "24.88", "34.88", 29, "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80", Map.of("color", "Blue"), 28),
                    item("SKU-1005", "123456789016", "Great Value Sparkling Water 12-Pack", "Great Value", "Grocery & Essentials", "Lime flavored sparkling water multipack.", "4.98", "4.98", 0, "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=900&q=80", Map.of("flavor", "Lime"), 140),
                    item("SKU-1006", "123456789017", "Equate Daily Vitamin Gummies", "Equate", "Health & Wellness", "Berry gummy vitamins for everyday wellness support.", "10.47", "10.47", 0, "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=900&q=80", Map.of("type", "Gummies"), 72),
                    item("SKU-1007", "123456789018", "George Men's Crew T-Shirt 3-Pack", "George", "Clothing & Shoes", "Soft crewneck t-shirts for everyday wear.", "14.96", "14.96", 0, "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80", Map.of("fit", "Regular"), 64),
                    item("SKU-1008", "123456789019", "Hyper Tough 20V Drill Kit", "Hyper Tough", "Home & Garden", "Cordless drill kit with charger and bit set. Great for home improvement projects.", "49.97", "69.97", 29, "https://images.unsplash.com/photo-1504148455328-c376907d081c?auto=format&fit=crop&w=900&q=80", Map.of("battery", "20V"), 18),
                    item("SKU-1009", "123456789020", "Little Tikes Classic Toy Chest", "Little Tikes", "Toys & Games", "Kids toy storage chest, perfect for organizing toys and games.", "39.99", "49.99", 20, "https://images.unsplash.com/photo-1559827260-dc66d52bef19?auto=format&fit=crop&w=900&q=80", Map.of("age", "2+"), 52),
                    item("SKU-1010", "123456789021", "Huggies Newborn Diapers Size 1", "Huggies", "Baby", "Soft newborn diapers with wetness indicator, pack of 84.", "24.97", "24.97", 0, "https://images.unsplash.com/photo-1604068549290-dea0e4a305ca?auto=format&fit=crop&w=900&q=80", Map.of("size", "Newborn"), 120),
                    item("SKU-1011", "123456789022", "EvoShield Youth Baseball Helmet", "EvoShield", "Sports & Outdoors", "Youth batting helmet for baseball and softball.", "34.99", "44.99", 22, "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=900&q=80", Map.of("sport", "Baseball"), 30),
                    item("SKU-1012", "123456789023", "Equate Beauty Moisturizing Face Wash", "Equate Beauty", "Beauty", "Gentle daily face wash for all skin types.", "6.97", "6.97", 0, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80", Map.of("skin", "All types"), 85),
                    item("SKU-1013", "1234567890241", "KidKraft Wooden Toy Blocks Set", "KidKraft", "Toys & Games", "Classic toy blocks for building, stacking, and creative play.", "19.99", "24.99", 0, "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4?auto=format&fit=crop&w=900&q=80", Map.of("age", "3+"), 64),
                    item("SKU-1014", "123456789025", "Mainstays 4-Piece Bedding Set", "Mainstays", "Home & Garden", "Soft bedding set for the bedroom with pillowcases and a comforter.", "34.99", "44.99", 22, "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80", Map.of("size", "Queen"), 38),
                    item("SKU-1015", "123456789026", "Mainstays Decorative Table Lamp", "Mainstays", "Home & Garden", "Warm bedside lamp for reading and bedroom decor.", "21.98", "29.98", 0, "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80", Map.of("finish", "White"), 41),
                    item("SKU-1016", "123456789027", "RCA Streaming Stick", "RCA", "Electronics", "Compact streaming device for smart TV apps and entertainment.", "29.99", "39.99", 0, "https://images.unsplash.com/photo-1511707171634-5f897ff02aa9?auto=format&fit=crop&w=900&q=80", Map.of("type", "Streaming"), 52),
                    item("SKU-1017", "123456789028", "JBL Portable Bluetooth Speaker", "JBL", "Electronics", "Water-resistant speaker with rich sound and wireless pairing.", "54.00", "69.00", 22, "https://images.unsplash.com/photo-1512446733611-9099a758e0c4?auto=format&fit=crop&w=900&q=80", Map.of("color", "Black"), 44),
                    item("SKU-1018", "123456789029", "SanDisk 128GB microSD Card", "SanDisk", "Electronics", "High-speed memory card for phones, tablets, and cameras.", "17.99", "24.99", 0, "https://images.unsplash.com/photo-1518770660439-4636190af475?auto=format&fit=crop&w=900&q=80", Map.of("capacity", "128GB"), 110),
                    item("SKU-1019", "123456789030", "Anker USB-C Charging Cable", "Anker", "Electronics", "Durable fast-charging cable for phones and laptops.", "12.99", "16.99", 0, "https://images.unsplash.com/photo-1580894732444-8ecded7900cd?auto=format&fit=crop&w=900&q=80", Map.of("length", "6ft"), 88),
                    item("SKU-1020", "123456789031", "Coleman Waterproof Picnic Blanket", "Coleman", "Sports & Outdoors", "Large picnic blanket for the park, beach, or campsite.", "24.99", "34.99", 0, "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80", Map.of("size", "Large"), 47),
                    item("SKU-1021", "123456789032", "Franklin Soccer Ball", "Franklin", "Sports & Outdoors", "Durable soccer ball for practice and neighborhood games.", "14.99", "19.99", 25, "https://images.unsplash.com/photo-1519861531473-9200262188bf?auto=format&fit=crop&w=900&q=80", Map.of("sport", "Soccer"), 61),
                    item("SKU-1022", "123456789033", "Fitbit Inspire Fitness Band", "Fitbit", "Sports & Outdoors", "Lightweight fitness tracker for daily activity and sleep.", "69.95", "89.95", 0, "https://images.unsplash.com/photo-1518611012118-696072aa579a?auto=format&fit=crop&w=900&q=80", Map.of("color", "Black"), 26),
                    item("SKU-1023", "123456789034", "Crest Toothpaste 2-Pack", "Crest", "Grocery & Essentials", "Fresh mint toothpaste pack for daily oral care.", "6.99", "8.99", 22, "https://images.unsplash.com/photo-1515377905703-c4788e51af15?auto=format&fit=crop&w=900&q=80", Map.of("type", "Oral care"), 95),
                    item("SKU-1024", "123456789035", "Quaker Instant Oatmeal Variety Pack", "Quaker", "Grocery & Essentials", "Breakfast oatmeal cups with mixed flavors.", "7.48", "9.48", 0, "https://images.unsplash.com/photo-1498837167922-ddd27525d352?auto=format&fit=crop&w=900&q=80", Map.of("flavor", "Mixed"), 74),
                    item("SKU-1025", "123456789036", "Hunt's Tomato Sauce 8-Pack", "Hunt's", "Grocery & Essentials", "Pantry tomato sauce for pasta and cooking.", "5.96", "7.96", 0, "https://images.unsplash.com/photo-1547592180-85f173990554?auto=format&fit=crop&w=900&q=80", Map.of("size", "8 Pack"), 83),
                    item("SKU-1026", "123456789037", "Planters Mixed Nuts Can", "Planters", "Grocery & Essentials", "Roasted mixed nuts snack for travel or office.", "8.98", "11.98", 0, "https://images.unsplash.com/photo-1508747703725-719777637510?auto=format&fit=crop&w=900&q=80", Map.of("type", "Snack"), 66),
                    item("SKU-1027", "123456789038", "Nature Made Vitamin C Tablets", "Nature Made", "Health & Wellness", "Daily vitamin C supplement with immune support.", "12.98", "15.98", 0, "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=900&q=80", Map.of("count", "100"), 104),
                    item("SKU-1028", "123456789039", "TheraBreath Mouthwash", "TheraBreath", "Health & Wellness", "Alcohol-free mouthwash with fresh breath care.", "9.97", "12.97", 0, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80", Map.of("type", "Oral care"), 58),
                    item("SKU-1029", "123456789040", "Band-Aid Adhesive Bandages", "Band-Aid", "Health & Wellness", "Assorted bandages for minor cuts and scrapes.", "4.48", "5.48", 0, "https://images.unsplash.com/photo-1583947215259-38e31be8751f?auto=format&fit=crop&w=900&q=80", Map.of("count", "100"), 132),
                    item("SKU-1030", "123456789041", "Vicks Thermometer", "Vicks", "Health & Wellness", "Digital thermometer for quick temperature checks.", "14.97", "19.97", 0, "https://images.unsplash.com/photo-1580281657527-47f249e8f7e6?auto=format&fit=crop&w=900&q=80", Map.of("type", "Digital"), 41),
                    item("SKU-1031", "123456789042", "Wonder Nation Kids Hoodie", "Wonder Nation", "Clothing & Shoes", "Comfortable kids hoodie for cool weather.", "12.98", "16.98", 0, "https://images.unsplash.com/photo-1523398002811-999ca8dec234?auto=format&fit=crop&w=900&q=80", Map.of("size", "M"), 72),
                    item("SKU-1032", "123456789043", "Athletic Works Running Shorts", "Athletic Works", "Clothing & Shoes", "Lightweight running shorts for workouts and casual wear.", "11.94", "14.94", 0, "https://images.unsplash.com/photo-1515886657613-9f3515b0c78f?auto=format&fit=crop&w=900&q=80", Map.of("color", "Navy"), 67),
                    item("SKU-1033", "123456789044", "Skechers Memory Foam Sneakers", "Skechers", "Clothing & Shoes", "Everyday sneakers with cushioned memory foam comfort.", "39.98", "49.98", 0, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80", Map.of("size", "10"), 33),
                    item("SKU-1034", "123456789045", "Time and Tru Denim Jacket", "Time and Tru", "Clothing & Shoes", "Classic denim jacket for layering.", "24.98", "29.98", 0, "https://images.unsplash.com/photo-1523398002811-999ca8dec234?auto=format&fit=crop&w=900&q=80", Map.of("wash", "Blue"), 45),
                    item("SKU-1035", "123456789046", "Melissa & Doug Wooden Puzzle", "Melissa & Doug", "Toys & Games", "Wooden puzzle for early learning and play.", "9.99", "12.99", 0, "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4?auto=format&fit=crop&w=900&q=80", Map.of("age", "4+"), 89),
                    item("SKU-1036", "123456789047", "Hot Wheels 10-Car Pack", "Hot Wheels", "Toys & Games", "Mini toy cars set for racing and collecting.", "14.98", "19.98", 25, "https://images.unsplash.com/photo-1501601963275-85d6b7f72dc7?auto=format&fit=crop&w=900&q=80", Map.of("type", "Cars"), 77),
                    item("SKU-1037", "123456789048", "LEGO Classic Brick Box", "LEGO", "Toys & Games", "Creative brick set for building and imagination.", "44.99", "54.99", 0, "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4?auto=format&fit=crop&w=900&q=80", Map.of("age", "5+"), 29),
                    item("SKU-1038", "123456789049", "Pampers Swaddlers Size 2", "Pampers", "Baby", "Soft diapers designed for infants and newborn care.", "26.97", "29.97", 0, "https://images.unsplash.com/photo-1604068549290-dea0e4a305ca?auto=format&fit=crop&w=900&q=80", Map.of("size", "2"), 111),
                    item("SKU-1039", "123456789050", "Graco Baby Wipes 3-Pack", "Graco", "Baby", "Gentle wipes for diaper changes and daily cleanup.", "7.97", "9.97", 0, "https://images.unsplash.com/photo-1542444459-db4a7e4f42ae?auto=format&fit=crop&w=900&q=80", Map.of("pack", "3"), 94),
                    item("SKU-1040", "123456789051", "Fisher-Price Activity Gym", "Fisher-Price", "Baby", "Play gym with colors and textures for infant development.", "34.97", "44.97", 0, "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4?auto=format&fit=crop&w=900&q=80", Map.of("age", "0+"), 43),
                    item("SKU-1041", "123456789052", "Munchkin Bottle Brush Set", "Munchkin", "Baby", "Cleaning set for baby bottles and accessories.", "5.97", "7.97", 0, "https://images.unsplash.com/photo-1604068549290-dea0e4a305ca?auto=format&fit=crop&w=900&q=80", Map.of("type", "Cleaning"), 68),
                    item("SKU-1042", "123456789053", "CeraVe Moisturizing Lotion", "CeraVe", "Beauty", "Daily moisturizing lotion for dry skin.", "15.97", "18.97", 0, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80", Map.of("skin", "Dry"), 80),
                    item("SKU-1043", "123456789054", "L'Oréal Voluminous Mascara", "L'Oréal", "Beauty", "Lengthening mascara for bold lashes.", "10.98", "13.98", 0, "https://images.unsplash.com/photo-1487412720507-e7ab37603c6f?auto=format&fit=crop&w=900&q=80", Map.of("color", "Black"), 57),
                    item("SKU-1044", "123456789055", "Neutrogena Face Moisturizer", "Neutrogena", "Beauty", "Lightweight daily moisturizer for face care.", "11.97", "14.97", 0, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80", Map.of("skin", "Sensitive"), 73),
                    item("SKU-1045", "123456789056", "Dove Body Wash Refill", "Dove", "Beauty", "Gentle body wash refill with a fresh scent.", "8.96", "10.96", 0, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80", Map.of("type", "Body wash"), 91),
                    item("SKU-1046", "123456789057", "Fresh Bananas 2 lb", "Great Value", "Fruits & Produce", "Fresh bananas for breakfast, smoothies, or snacks.", "1.98", "2.98", 0, "https://images.unsplash.com/photo-1571771894821-ce9b6c11b08e?auto=format&fit=crop&w=900&q=80", Map.of("type", "Fruit"), 150),
                    item("SKU-1047", "123456789058", "Honeycrisp Apples 3 lb Bag", "Great Value", "Fruits & Produce", "Crisp apples for lunch boxes and baking.", "4.98", "5.98", 0, "https://images.unsplash.com/photo-1560806887-1e4cd0b6cbd6?auto=format&fit=crop&w=900&q=80", Map.of("type", "Fruit"), 132),
                    item("SKU-1048", "123456789059", "Organic Blueberries", "Marketside", "Fruits & Produce", "Sweet blueberries for yogurt, smoothies, and fruit bowls.", "3.97", "4.97", 0, "https://images.unsplash.com/photo-1498557850523-fd3d118b962e?auto=format&fit=crop&w=900&q=80", Map.of("type", "Fruit"), 101),
                    item("SKU-1049", "123456789060", "Pineapple Chunks Cup", "Great Value", "Fruits & Produce", "Ready-to-eat pineapple fruit cup for a quick snack.", "2.48", "3.48", 0, "https://images.unsplash.com/photo-1550258987-190a2d41a8ba?auto=format&fit=crop&w=900&q=80", Map.of("type", "Fruit"), 88)
            ).forEach(seed -> ensureItem(itemRepository, seed));
        };
    }

    private void ensureItem(ItemRepository itemRepository, ItemDocument seed) {
        itemRepository.findBySku(seed.getSku()).ifPresentOrElse(existing -> {
            seed.setId(existing.getId());
            itemRepository.save(seed);
        }, () -> itemRepository.findByUpc(seed.getUpc()).ifPresentOrElse(existing -> {
            seed.setId(existing.getId());
            itemRepository.save(seed);
        }, () -> itemRepository.save(seed)));
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
