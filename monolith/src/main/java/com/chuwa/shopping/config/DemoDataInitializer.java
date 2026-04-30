package com.chuwa.shopping.config;

import com.chuwa.shopping.security.dao.RoleRepository;
import com.chuwa.shopping.security.dao.UserRepository;
import com.chuwa.shopping.security.entity.Role;
import com.chuwa.shopping.security.entity.User;
import com.chuwa.shopping.account.dao.CustomerAccountRepository;
import com.chuwa.shopping.account.entity.AccountStatus;
import com.chuwa.shopping.account.entity.AddressType;
import com.chuwa.shopping.account.entity.CustomerAccount;
import com.chuwa.shopping.account.entity.CustomerAddress;
import com.chuwa.shopping.account.entity.MembershipLevel;
import com.chuwa.shopping.account.entity.StoredPaymentMethod;
import com.chuwa.shopping.item.dao.ItemRepository;
import com.chuwa.shopping.item.entity.InventoryDocument;
import com.chuwa.shopping.item.entity.ItemDocument;
import com.chuwa.shopping.item.entity.ItemStatus;
import com.chuwa.shopping.payment.entity.PaymentMethod;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Configuration
public class DemoDataInitializer {

    @Bean
    public CommandLineRunner seedDemoData(RoleRepository roleRepository,
                                          UserRepository userRepository,
                                          CustomerAccountRepository customerAccountRepository,
                                          ItemRepository itemRepository,
                                          PasswordEncoder passwordEncoder) {
        return args -> {
            seedSecurity(roleRepository, userRepository, passwordEncoder);
            seedAccount(customerAccountRepository, passwordEncoder);
            seedCatalog(itemRepository);
        };
    }

    private void seedSecurity(RoleRepository roleRepository,
                              UserRepository userRepository,
                              PasswordEncoder passwordEncoder) {
        Role roleUser = roleRepository.findByName("ROLE_USER")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_USER");
                    return roleRepository.save(role);
                });

        roleRepository.findByName("ROLE_ADMIN")
                .orElseGet(() -> {
                    Role role = new Role();
                    role.setName("ROLE_ADMIN");
                    return roleRepository.save(role);
                });

        seedUser(userRepository, passwordEncoder, roleUser, "itest-user", "Taylor Jordan", "itest-user@example.com");
        seedUser(userRepository, passwordEncoder, roleUser, "premium-user", "Avery Morgan", "premium-user@example.com");
    }

    private void seedAccount(CustomerAccountRepository customerAccountRepository,
                             PasswordEncoder passwordEncoder) {
        seedAccountIfMissing(customerAccountRepository, passwordEncoder,
                "itest-user", "Taylor Jordan", "itest-user@example.com", "3125551111",
                MembershipLevel.REGULAR, "123 Main Street", "Apt 5B", "Chicago", "IL", "60601",
                "tok_demo_visa_primary", "**** **** **** 4242");
        seedAccountIfMissing(customerAccountRepository, passwordEncoder,
                "premium-user", "Avery Morgan", "premium-user@example.com", "7735552222",
                MembershipLevel.PREMIUM, "980 Lake Shore Drive", "Unit 17C", "Chicago", "IL", "60611",
                "tok_demo_mastercard_premium", "**** **** **** 5454");
    }

    private void seedCatalog(ItemRepository itemRepository) {
        if (itemRepository.count() > 0) {
            return;
        }

        itemRepository.saveAll(List.of(
                buildItem(
                        "SKU-1001", "123456789012", "Mainstays Ceramic Coffee Mug", "Mainstays", "Kitchen",
                        "White ceramic mug for coffee, tea, and desk setups.",
                        "12.99", "12.99", null, "https://images.unsplash.com/photo-1577937927133-66ef06acdf18?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "White", "material", "Ceramic"), 90, 10, "WH1"
                ),
                buildItem(
                        "SKU-1002", "123456789013", "onn. Bluetooth Headphones", "onn.", "Electronics",
                        "Over-ear wireless headphones with all-day playback.",
                        "39.00", "49.00", 20, "https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Black", "connectivity", "Bluetooth"), 36, 4, "WH1"
                ),
                buildItem(
                        "SKU-1003", "123456789014", "Better Homes & Gardens Throw Pillow", "Better Homes & Gardens", "Home",
                        "Textured accent pillow for sofa, reading nook, or guest room.",
                        "18.50", "18.50", null, "https://images.unsplash.com/photo-1505693416388-ac5ce068fe85?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Rust", "material", "Cotton Blend"), 55, 5, "WH2"
                ),
                buildItem(
                        "SKU-1004", "123456789015", "Ozark Trail Folding Camp Chair", "Ozark Trail", "Outdoor",
                        "Portable folding chair with cup holder for camping and sidelines.",
                        "24.88", "34.88", 29, "https://images.unsplash.com/photo-1500530855697-b586d89ba3ee?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Blue", "weightCapacity", "225 lb"), 28, 2, "WH2"
                ),
                buildItem(
                        "SKU-1005", "123456789016", "Great Value Sparkling Water 12-Pack", "Great Value", "Grocery",
                        "Lime flavored sparkling water multipack for pantry restock.",
                        "4.98", "4.98", null, "https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=900&q=80",
                        Map.of("flavor", "Lime", "packSize", "12"), 140, 20, "WH3"
                ),
                buildItem(
                        "SKU-1006", "123456789017", "Equate Daily Vitamin Gummies", "Equate", "Health",
                        "Berry gummy vitamins for everyday wellness support.",
                        "10.47", "10.47", null, "https://images.unsplash.com/photo-1584308666744-24d5c474f2ae?auto=format&fit=crop&w=900&q=80",
                        Map.of("type", "Gummies", "count", "70"), 72, 9, "WH3"
                ),
                buildItem(
                        "SKU-1007", "123456789018", "George Men's Crew T-Shirt 3-Pack", "George", "Apparel",
                        "Soft crewneck t-shirts designed for everyday wear.",
                        "14.96", "14.96", null, "https://images.unsplash.com/photo-1521572163474-6864f9cf17ab?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Assorted", "sizeRange", "S-XXL"), 64, 7, "WH4"
                ),
                buildItem(
                        "SKU-1008", "123456789019", "Hyper Tough 20V Drill Kit", "Hyper Tough", "Tools",
                        "Cordless drill kit with charger and starter bit set.",
                        "49.97", "69.97", 29, "https://images.unsplash.com/photo-1504148455328-c376907d081c?auto=format&fit=crop&w=900&q=80",
                        Map.of("battery", "20V", "kit", "Starter"), 18, 3, "WH4"
                ),
                buildItem(
                        "SKU-1009", "123456789020", "Pioneer Woman Floral Dinner Plate Set", "The Pioneer Woman", "Kitchen",
                        "Colorful floral dinner plates sized for family meals and entertaining.",
                        "22.94", "22.94", null, "https://images.unsplash.com/photo-1498654896293-37aacf113fd9?auto=format&fit=crop&w=900&q=80",
                        Map.of("pieces", "4", "material", "Stoneware"), 34, 3, "WH1"
                ),
                buildItem(
                        "SKU-1010", "123456789021", "Apple AirPods Case Cover", "heyday", "Electronics",
                        "Protective silicone cover with ring clip for everyday carry.",
                        "9.88", "12.88", 23, "https://images.unsplash.com/photo-1606220588913-b3aacb4d2f46?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Navy", "compatibility", "AirPods"), 44, 5, "WH1"
                ),
                buildItem(
                        "SKU-1011", "123456789022", "Better Homes & Gardens Scented Candle", "Better Homes & Gardens", "Home",
                        "Large jar candle with warm cedar and vanilla fragrance.",
                        "7.44", "7.44", null, "https://images.unsplash.com/photo-1603006905003-be475563bc59?auto=format&fit=crop&w=900&q=80",
                        Map.of("scent", "Cedar Vanilla", "size", "18 oz"), 61, 6, "WH2"
                ),
                buildItem(
                        "SKU-1012", "123456789023", "Mainstays 3-Tier Storage Cart", "Mainstays", "Home",
                        "Rolling utility cart for pantry, laundry room, or dorm storage.",
                        "29.96", "39.96", 25, "https://images.unsplash.com/photo-1582582621959-48d27397dc69?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "White", "tiers", "3"), 22, 2, "WH2"
                ),
                buildItem(
                        "SKU-1013", "123456789024", "Great Value Organic Bananas", "Great Value", "Grocery",
                        "Fresh organic bananas sold by the bunch.",
                        "1.98", "1.98", null, "https://images.unsplash.com/photo-1574226516831-e1dff420e37f?auto=format&fit=crop&w=900&q=80",
                        Map.of("organic", "Yes", "unit", "Bunch"), 120, 8, "WH3"
                ),
                buildItem(
                        "SKU-1014", "123456789025", "Fresh Strawberries 1 lb", "Freshness Guaranteed", "Grocery",
                        "Sweet strawberries for snacking, baking, and smoothies.",
                        "3.97", "5.47", 27, "https://images.unsplash.com/photo-1464965911861-746a04b4bca6?auto=format&fit=crop&w=900&q=80",
                        Map.of("size", "1 lb", "fresh", "Yes"), 76, 7, "WH3"
                ),
                buildItem(
                        "SKU-1015", "123456789026", "Equate Gentle Facial Cleanser", "Equate", "Health",
                        "Daily facial cleanser for a simple skin-care routine.",
                        "6.67", "8.67", 23, "https://images.unsplash.com/photo-1556228578-8c89e6adf883?auto=format&fit=crop&w=900&q=80",
                        Map.of("skinType", "Sensitive", "size", "12 oz"), 58, 6, "WH3"
                ),
                buildItem(
                        "SKU-1016", "123456789027", "No Boundaries Women's Crew Socks 10-Pack", "No Boundaries", "Apparel",
                        "Cotton blend crew socks in mixed neutral colors.",
                        "8.98", "8.98", null, "https://images.unsplash.com/photo-1586350977771-b3b0abd50c82?auto=format&fit=crop&w=900&q=80",
                        Map.of("count", "10", "sizeRange", "4-10"), 47, 5, "WH4"
                ),
                buildItem(
                        "SKU-1017", "123456789028", "Athletic Works Stainless Steel Water Bottle", "Athletic Works", "Sports",
                        "Insulated reusable bottle for gym sessions and office commutes.",
                        "12.84", "12.84", null, "https://images.unsplash.com/photo-1602143407151-7111542de6e8?auto=format&fit=crop&w=900&q=80",
                        Map.of("capacity", "32 oz", "color", "Teal"), 39, 4, "WH4"
                ),
                buildItem(
                        "SKU-1018", "123456789029", "Ozark Trail Instant Canopy", "Ozark Trail", "Outdoor",
                        "Easy pop-up canopy for tailgates, markets, and backyard events.",
                        "89.00", "89.00", null, "https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?auto=format&fit=crop&w=900&q=80",
                        Map.of("size", "10x10", "color", "Blue"), 12, 2, "WH4"
                ),
                buildItem(
                        "SKU-1019", "123456789030", "LEGO Classic Creative Brick Box", "LEGO", "Toys",
                        "Starter brick set for open-ended building and family play.",
                        "24.99", "31.99", 22, "https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=900&q=80",
                        Map.of("pieces", "484", "age", "4+"), 25, 3, "WH5"
                ),
                buildItem(
                        "SKU-1020", "123456789031", "HP 15.6\" Laptop Backpack", "HP", "Electronics",
                        "Padded backpack with laptop sleeve and organizer pockets.",
                        "34.76", "44.76", 22, "https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80",
                        Map.of("fits", "15.6 inch", "color", "Gray"), 31, 3, "WH5"
                ),
                buildItem(
                        "SKU-1021", "123456789032", "Mainstays Blackout Curtain Panel", "Mainstays", "Home",
                        "Room-darkening curtain panel for bedrooms and media rooms.",
                        "16.87", "16.87", null, "https://images.unsplash.com/photo-1513694203232-719a280e022f?auto=format&fit=crop&w=900&q=80",
                        Map.of("size", "84 in", "color", "Navy"), 49, 5, "WH2"
                ),
                buildItem(
                        "SKU-1022", "123456789033", "Great Value Ground Coffee Medium Roast", "Great Value", "Grocery",
                        "Medium roast ground coffee for everyday brewing.",
                        "9.42", "12.42", 24, "https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=80",
                        Map.of("roast", "Medium", "size", "24.2 oz"), 67, 8, "WH3"
                ),
                buildItem(
                        "SKU-1023", "123456789034", "Parent's Choice Baby Wipes 4-Pack", "Parent's Choice", "Baby",
                        "Value pack of fragrance-free baby wipes for daily care.",
                        "7.98", "10.98", 27, "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4?auto=format&fit=crop&w=900&q=80",
                        Map.of("count", "320", "fragrance", "Free"), 53, 6, "WH5"
                ),
                buildItem(
                        "SKU-1024", "123456789035", "Hyper Tough 50-Piece Tool Set", "Hyper Tough", "Tools",
                        "Compact household tool kit for quick repairs and apartment setups.",
                        "19.97", "19.97", null, "https://images.unsplash.com/photo-1581147036324-c1c7b99cf8fb?auto=format&fit=crop&w=900&q=80",
                        Map.of("pieces", "50", "case", "Included"), 27, 4, "WH4"
                ),
                buildItem(
                        "SKU-1025", "123456789036", "Mainstays Plush Bath Towel", "Mainstays", "Home",
                        "Soft oversized bath towel for everyday use and guest bathrooms.",
                        "8.97", "10.97", 18, "https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Gray", "size", "30 x 54"), 73, 8, "WH2"
                ),
                buildItem(
                        "SKU-1026", "123456789037", "Samsung 32\\\" Smart HD TV", "Samsung", "Electronics",
                        "Compact smart TV with streaming apps for bedrooms and apartments.",
                        "158.00", "178.00", 11, "https://images.unsplash.com/photo-1593784991095-a205069470b6?auto=format&fit=crop&w=900&q=80",
                        Map.of("resolution", "HD", "screenSize", "32 inch"), 14, 2, "WH5"
                ),
                buildItem(
                        "SKU-1027", "123456789038", "Great Value Frozen Cheese Pizza", "Great Value", "Grocery",
                        "Family-size frozen cheese pizza for easy weeknight dinners.",
                        "5.86", "5.86", null, "https://images.unsplash.com/photo-1513104890138-7c749659a591?auto=format&fit=crop&w=900&q=80",
                        Map.of("size", "15.2 oz", "type", "Frozen"), 96, 10, "WH3"
                ),
                buildItem(
                        "SKU-1028", "123456789039", "Equate Ibuprofen Caplets", "Equate", "Health",
                        "Pain relief caplets for travel bags, desks, and home medicine cabinets.",
                        "6.44", "8.44", 24, "https://images.unsplash.com/photo-1471864190281-a93a3070b6de?auto=format&fit=crop&w=900&q=80",
                        Map.of("count", "100", "strength", "200 mg"), 82, 9, "WH3"
                ),
                buildItem(
                        "SKU-1029", "123456789040", "George Zip Hoodie", "George", "Apparel",
                        "Midweight zip hoodie with front pockets for layering year-round.",
                        "17.98", "24.98", 28, "https://images.unsplash.com/photo-1556821840-3a63f95609a7?auto=format&fit=crop&w=900&q=80",
                        Map.of("color", "Heather Gray", "sizeRange", "S-XXL"), 42, 5, "WH4"
                ),
                buildItem(
                        "SKU-1030", "123456789041", "Spalding Indoor/Outdoor Basketball", "Spalding", "Sports",
                        "Durable basketball for driveways, gyms, and park courts.",
                        "18.74", "22.74", 18, "https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=900&q=80",
                        Map.of("size", "29.5", "use", "Indoor/Outdoor"), 29, 4, "WH4"
                ),
                buildItem(
                        "SKU-1031", "123456789042", "Graco SlimFit Car Seat", "Graco", "Baby",
                        "Convertible car seat with slim design for growing families.",
                        "179.00", "199.00", 10, "https://images.unsplash.com/photo-1515488042361-ee00e0ddd4e4?auto=format&fit=crop&w=900&q=80",
                        Map.of("weightRange", "5-100 lb", "color", "Redmond"), 11, 2, "WH5"
                ),
                buildItem(
                        "SKU-1032", "123456789043", "Play-Doh 24-Pack Colors", "Play-Doh", "Toys",
                        "Multi-color modeling compound set for crafts and classroom fun.",
                        "14.84", "14.84", null, "https://images.unsplash.com/photo-1516627145497-ae6968895b74?auto=format&fit=crop&w=900&q=80",
                        Map.of("count", "24", "age", "2+"), 38, 4, "WH5"
                ),
                buildItem(
                        "SKU-1033", "123456789044", "Ozark Trail 30-Can Cooler", "Ozark Trail", "Outdoor",
                        "Soft-sided cooler with shoulder strap for park days and road trips.",
                        "24.97", "29.97", 17, "https://images.unsplash.com/photo-1500534623283-312aade485b7?auto=format&fit=crop&w=900&q=80",
                        Map.of("capacity", "30 cans", "color", "Blue"), 24, 3, "WH4"
                ),
                buildItem(
                        "SKU-1034", "123456789045", "Keurig K-Express Coffee Maker", "Keurig", "Kitchen",
                        "Single-serve coffee maker with strong brew option and compact footprint.",
                        "69.00", "89.00", 22, "https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?auto=format&fit=crop&w=900&q=80",
                        Map.of("brewSizes", "8/10/12 oz", "color", "Black"), 16, 2, "WH1"
                ),
                buildItem(
                        "SKU-1035", "123456789046", "Bounty Paper Towels 6 Double Rolls", "Bounty", "Home",
                        "Absorbent paper towels for everyday kitchen and cleanup needs.",
                        "14.98", "16.98", 12, "https://images.unsplash.com/photo-1583947582886-f40ec95dd752?auto=format&fit=crop&w=900&q=80",
                        Map.of("rolls", "6 double", "strength", "2-ply"), 57, 7, "WH2"
                ),
                buildItem(
                        "SKU-1036", "123456789047", "Apple Watch SE 40mm", "Apple", "Electronics",
                        "Fitness, messaging, and daily activity tracking in a lightweight smartwatch.",
                        "219.00", "249.00", 12, "https://images.unsplash.com/photo-1546868871-7041f2a55e12?auto=format&fit=crop&w=900&q=80",
                        Map.of("size", "40mm", "connectivity", "GPS"), 9, 1, "WH5"
                )
        ));
    }

    private void seedUser(UserRepository userRepository,
                          PasswordEncoder passwordEncoder,
                          Role roleUser,
                          String accountName,
                          String fullName,
                          String email) {
        if (userRepository.findByAccount(accountName).isPresent()) {
            return;
        }
        User user = new User();
        user.setName(fullName);
        user.setAccount(accountName);
        user.setEmail(email);
        user.setPassword(passwordEncoder.encode("Passw0rd!"));
        user.setRoles(Set.of(roleUser));
        userRepository.save(user);
    }

    private void seedAccountIfMissing(CustomerAccountRepository customerAccountRepository,
                                      PasswordEncoder passwordEncoder,
                                      String username,
                                      String fullName,
                                      String email,
                                      String phoneNumber,
                                      MembershipLevel membershipLevel,
                                      String addressLine1,
                                      String addressLine2,
                                      String city,
                                      String state,
                                      String postalCode,
                                      String accountToken,
                                      String maskedNumber) {
        CustomerAccount account = customerAccountRepository.findByUsername(username).orElseGet(CustomerAccount::new);
        boolean newAccount = account.getId() == null;

        account.setUsername(username);
        account.setFullName(fullName);
        account.setEmail(email);
        account.setPassword(passwordEncoder.encode("Passw0rd!"));
        account.setPhoneNumber(phoneNumber);
        account.setStatus(AccountStatus.ACTIVE);
        account.setMembershipLevel(membershipLevel);

        if (newAccount && account.getAddresses().isEmpty()) {
            CustomerAddress shipping = new CustomerAddress();
            shipping.setLabel("Home");
            shipping.setRecipientName(fullName);
            shipping.setAddressLine1(addressLine1);
            shipping.setAddressLine2(addressLine2);
            shipping.setCity(city);
            shipping.setState(state);
            shipping.setPostalCode(postalCode);
            shipping.setCountry("US");
            shipping.setAddressType(AddressType.SHIPPING);
            shipping.setDefaultAddress(true);
            shipping.setCustomerAccount(account);

            CustomerAddress billing = new CustomerAddress();
            billing.setLabel("Billing");
            billing.setRecipientName(fullName);
            billing.setAddressLine1(addressLine1);
            billing.setAddressLine2(addressLine2);
            billing.setCity(city);
            billing.setState(state);
            billing.setPostalCode(postalCode);
            billing.setCountry("US");
            billing.setAddressType(AddressType.BILLING);
            billing.setDefaultAddress(false);
            billing.setCustomerAccount(account);

            account.getAddresses().add(shipping);
            account.getAddresses().add(billing);
        }

        ensurePaymentMethod(account, PaymentMethod.CREDIT_CARD, "Visa", accountToken, maskedNumber, fullName, true);
        ensurePaymentMethod(account, PaymentMethod.DEBIT_CARD, "Mastercard", username + "_debit", "**** **** **** 2727", fullName, false);
        ensurePaymentMethod(account, PaymentMethod.WALLET, "Digital wallet", username + "_wallet", username + "@wallet", fullName, false);

        customerAccountRepository.save(account);
    }

    private void ensurePaymentMethod(CustomerAccount account,
                                     PaymentMethod paymentMethod,
                                     String provider,
                                     String token,
                                     String maskedNumber,
                                     String fullName,
                                     boolean defaultMethod) {
        boolean exists = account.getPaymentMethods().stream()
                .anyMatch(method -> token.equals(method.getAccountToken()));
        if (exists) {
            return;
        }
        StoredPaymentMethod method = new StoredPaymentMethod();
        method.setPaymentMethodType(paymentMethod);
        method.setProvider(provider);
        method.setAccountToken(token);
        method.setMaskedNumber(maskedNumber);
        method.setCardholderName(fullName);
        method.setExpiryMonth(defaultMethod ? 12 : null);
        method.setExpiryYear(defaultMethod ? 2028 : null);
        method.setDefaultMethod(defaultMethod);
        method.setActive(true);
        method.setCustomerAccount(account);
        account.getPaymentMethods().add(method);
    }

    private ItemDocument buildItem(String sku,
                                   String upc,
                                   String itemName,
                                   String brand,
                                   String category,
                                   String description,
                                   String unitPrice,
                                   String listPrice,
                                   Integer discountPercent,
                                   String imageUrl,
                                   Map<String, String> attributes,
                                   int availableQuantity,
                                   int reservedQuantity,
                                   String warehouseCode) {
        ItemDocument item = new ItemDocument();
        item.setSku(sku);
        item.setUpc(upc);
        item.setItemName(itemName);
        item.setBrand(brand);
        item.setCategory(category);
        item.setDescription(description);
        item.setUnitPrice(new BigDecimal(unitPrice));
        item.setListPrice(listPrice == null ? null : new BigDecimal(listPrice));
        item.setDiscountPercent(discountPercent);
        item.setCurrencyCode("USD");
        item.setPictureUrls(List.of(imageUrl));
        item.setStatus(ItemStatus.ACTIVE);
        item.setAttributes(attributes);
        item.setCreatedAt(LocalDateTime.now());

        InventoryDocument inventory = new InventoryDocument();
        inventory.setTotalQuantity(availableQuantity + reservedQuantity);
        inventory.setAvailableQuantity(availableQuantity);
        inventory.setReservedQuantity(reservedQuantity);
        inventory.setReorderLevel(10);
        inventory.setWarehouseCode(warehouseCode);
        inventory.setInStock(availableQuantity > 0);
        inventory.setLastRestockedAt(LocalDateTime.now().minusDays(2));
        item.setInventory(inventory);

        return item;
    }
}
