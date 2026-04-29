const storageKeys = {
    token: "shopping.token",
    username: "shopping.username",
    expiresIn: "shopping.expiresIn",
    customerId: "shopping.customerId",
    lastOrder: "shopping.lastOrderNumber",
    lastPayment: "shopping.lastPaymentNumber",
    selectedCartItems: "shopping.selectedCartItems",
    wishlistItems: "shopping.wishlistItems"
};

const state = {
    token: localStorage.getItem(storageKeys.token) || "",
    username: localStorage.getItem(storageKeys.username) || "",
    expiresIn: localStorage.getItem(storageKeys.expiresIn) || "",
    customerId: Number(localStorage.getItem(storageKeys.customerId) || 0),
    lastOrderNumber: localStorage.getItem(storageKeys.lastOrder) || "",
    lastPaymentNumber: localStorage.getItem(storageKeys.lastPayment) || "",
    account: null,
    cart: null,
    order: null,
    payment: null,
    editingAddressIndex: -1,
    editingPaymentIndex: -1,
    itemCatalog: null
};

const DEMO_PICKUP_LOCATION = "ShopSmart Supercenter, 2825 N Ashland Ave, Chicago, IL";

function currentReturnTo() {
    return `${window.location.pathname}${window.location.search}`;
}

function normalizeFulfillmentMethod(value) {
    const normalized = String(value || "").toUpperCase();
    return normalized === "PICKUP" ? "PICKUP" : "SHIPPING";
}

function fulfillmentFromItem(item = {}) {
    if (String(item.fulfillmentMethod || "").toUpperCase() === "PICKUP") return "PICKUP";
    if (String(item.itemId || "").endsWith("::PICKUP")) return "PICKUP";
    return "SHIPPING";
}

function composeCartItemId(sku, fulfillmentMethod) {
    return `${sku}::${normalizeFulfillmentMethod(fulfillmentMethod)}`;
}

function pickupLocationLabel(value) {
    return DEMO_PICKUP_LOCATION;
}

function isPickupItem(item) {
    return fulfillmentFromItem(item) === "PICKUP";
}

function isShippingItem(item) {
    return fulfillmentFromItem(item) === "SHIPPING";
}

function groupCartItemsByFulfillment(items = []) {
    return {
        shipping: items.filter(isShippingItem),
        pickup: items.filter(isPickupItem)
    };
}

function countUnits(items = []) {
    return items.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
}

function fulfillmentSummary(items = []) {
    const groups = groupCartItemsByFulfillment(items);
    return {
        shippingItems: groups.shipping,
        pickupItems: groups.pickup,
        shippingUnits: countUnits(groups.shipping),
        pickupUnits: countUnits(groups.pickup)
    };
}

const fallbackItems = [
    {
        sku: "SKU-1001",
        itemName: "Mainstays Ceramic Coffee Mug",
        brand: "Mainstays",
        category: "Kitchen",
        description: "White ceramic mug for coffee, tea, and desk setups.",
        unitPrice: 12.99,
        listPrice: 12.99,
        discountPercent: 0,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1577937927133-66ef06acdf18?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 90, inStock: true }
    },
    {
        sku: "SKU-1002",
        itemName: "onn. Bluetooth Headphones",
        brand: "onn.",
        category: "Electronics",
        description: "Over-ear wireless headphones with all-day playback.",
        unitPrice: 39.0,
        listPrice: 49.0,
        discountPercent: 20,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1505740420928-5e560c06d30e?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 36, inStock: true }
    },
    {
        sku: "SKU-1005",
        itemName: "Great Value Sparkling Water 12-Pack",
        brand: "Great Value",
        category: "Grocery",
        description: "Lime flavored sparkling water multipack for pantry restock.",
        unitPrice: 4.98,
        listPrice: 4.98,
        discountPercent: 0,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1513558161293-cdaf765ed2fd?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 140, inStock: true }
    },
    {
        sku: "SKU-1019",
        itemName: "LEGO Classic Creative Brick Box",
        brand: "LEGO",
        category: "Toys",
        description: "Starter brick set for open-ended building and family play.",
        unitPrice: 24.99,
        listPrice: 24.99,
        discountPercent: 0,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1587654780291-39c9404d746b?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 25, inStock: true }
    },
    {
        sku: "SKU-1020",
        itemName: "HP 15.6\" Laptop Backpack",
        brand: "HP",
        category: "Electronics",
        description: "Padded backpack with laptop sleeve and organizer pockets.",
        unitPrice: 34.76,
        listPrice: 44.76,
        discountPercent: 22,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1542291026-7eec264c27ff?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 31, inStock: true }
    },
    {
        sku: "SKU-1018",
        itemName: "Ozark Trail Instant Canopy",
        brand: "Ozark Trail",
        category: "Outdoor",
        description: "Easy pop-up canopy for tailgates, markets, and backyard events.",
        unitPrice: 89.0,
        listPrice: 89.0,
        discountPercent: 0,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1504280390367-361c6d9f38f4?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 12, inStock: true }
    },
    {
        sku: "SKU-1022",
        itemName: "Great Value Ground Coffee Medium Roast",
        brand: "Great Value",
        category: "Grocery",
        description: "Medium roast ground coffee for everyday brewing.",
        unitPrice: 9.42,
        listPrice: 12.42,
        discountPercent: 24,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1495474472287-4d71bcdd2085?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 67, inStock: true }
    },
    {
        sku: "SKU-1024",
        itemName: "Hyper Tough 50-Piece Tool Set",
        brand: "Hyper Tough",
        category: "Tools",
        description: "Compact household tool kit for quick repairs and apartment setups.",
        unitPrice: 19.97,
        listPrice: 19.97,
        discountPercent: 0,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1581147036324-c1c7b99cf8fb?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 27, inStock: true }
    },
    {
        sku: "SKU-1025",
        itemName: "Mainstays Plush Bath Towel",
        brand: "Mainstays",
        category: "Home",
        description: "Soft oversized bath towel for everyday use and guest bathrooms.",
        unitPrice: 8.97,
        listPrice: 10.97,
        discountPercent: 18,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1584622650111-993a426fbf0a?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 73, inStock: true }
    },
    {
        sku: "SKU-1026",
        itemName: "Samsung 32\" Smart HD TV",
        brand: "Samsung",
        category: "Electronics",
        description: "Compact smart TV with streaming apps for bedrooms and apartments.",
        unitPrice: 158.0,
        listPrice: 178.0,
        discountPercent: 11,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1593784991095-a205069470b6?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 14, inStock: true }
    },
    {
        sku: "SKU-1030",
        itemName: "Spalding Indoor/Outdoor Basketball",
        brand: "Spalding",
        category: "Sports",
        description: "Durable basketball for driveways, gyms, and park courts.",
        unitPrice: 18.74,
        listPrice: 22.74,
        discountPercent: 18,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1546519638-68e109498ffc?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 29, inStock: true }
    },
    {
        sku: "SKU-1034",
        itemName: "Keurig K-Express Coffee Maker",
        brand: "Keurig",
        category: "Kitchen",
        description: "Single-serve coffee maker with strong brew option and compact footprint.",
        unitPrice: 69.0,
        listPrice: 89.0,
        discountPercent: 22,
        currencyCode: "USD",
        pictureUrls: ["https://images.unsplash.com/photo-1517668808822-9ebb02f2a0e6?auto=format&fit=crop&w=900&q=80"],
        inventory: { availableQuantity: 16, inStock: true }
    }
];

const page = document.body.dataset.page;
const query = new URLSearchParams(window.location.search);

function formatMoney(value, currencyCode = "USD") {
    return new Intl.NumberFormat("en-US", { style: "currency", currency: currencyCode }).format(Number(value || 0));
}

function formatDate(value) {
    if (!value) return "Pending";
    return new Date(value).toLocaleString();
}

function displayOrderStatus(value) {
    switch (value) {
        case "CREATED":
            return "Placed";
        case "PAID":
            return "Paid";
        case "FAILED":
            return "Failed";
        case "CANCELLED":
            return "Canceled";
        case "REFUNDED":
            return "Refunded";
        default:
            return value || "-";
    }
}

function displayPaymentStatus(value) {
    switch (value) {
        case "CAPTURED":
            return "Paid";
        case "AUTHORIZED":
        case "SUBMITTED":
            return "Processing";
        case "REFUNDED":
            return "Refunded";
        case "REVERSED":
            return "Canceled";
        default:
            return value || "-";
    }
}

function escapeHtml(value) {
    return String(value ?? "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll("\"", "&quot;")
        .replaceAll("'", "&#39;");
}

function slugify(value) {
    return String(value || "featured")
        .trim()
        .toLowerCase()
        .replace(/&/g, "and")
        .replace(/[^a-z0-9]+/g, "-")
        .replace(/^-+|-+$/g, "") || "featured";
}

function saveSession() {
    localStorage.setItem(storageKeys.token, state.token);
    localStorage.setItem(storageKeys.username, state.username);
    localStorage.setItem(storageKeys.expiresIn, String(state.expiresIn || ""));
    localStorage.setItem(storageKeys.customerId, String(state.customerId));
    localStorage.setItem(storageKeys.lastOrder, state.lastOrderNumber || "");
    localStorage.setItem(storageKeys.lastPayment, state.lastPaymentNumber || "");
}

function clearSession() {
    state.token = "";
    state.username = "";
    state.expiresIn = "";
    state.customerId = 0;
    state.account = null;
    state.cart = null;
    state.order = null;
    state.payment = null;
    Object.values(storageKeys).forEach((key) => localStorage.removeItem(key));
}

function getSelectedCartItems() {
    try {
        return JSON.parse(localStorage.getItem(storageKeys.selectedCartItems) || "[]");
    } catch (error) {
        return [];
    }
}

function saveSelectedCartItems(itemIds) {
    localStorage.setItem(storageKeys.selectedCartItems, JSON.stringify(itemIds));
}

function normalizeCartItemId(value) {
    return value == null ? "" : String(value);
}

function normalizeSelectedCartItemsForCart(items) {
    const availableIds = new Set((items || []).map((item) => normalizeCartItemId(item.itemId)).filter(Boolean));
    if (!availableIds.size) {
        saveSelectedCartItems([]);
        return new Set();
    }

    const storedIds = getSelectedCartItems()
        .map(normalizeCartItemId)
        .filter((itemId) => availableIds.has(itemId));

    const nextIds = storedIds.length ? storedIds : Array.from(availableIds);
    saveSelectedCartItems(nextIds);
    return new Set(nextIds);
}

function getWishlistItems() {
    try {
        return JSON.parse(localStorage.getItem(storageKeys.wishlistItems) || "[]");
    } catch (error) {
        return [];
    }
}

function saveWishlistItems(items) {
    localStorage.setItem(storageKeys.wishlistItems, JSON.stringify(items));
}

function isWishlisted(sku) {
    return getWishlistItems().some((item) => item.sku === sku);
}

function toggleWishlist(item) {
    const items = getWishlistItems();
    const existingIndex = items.findIndex((entry) => entry.sku === item.sku);
    if (existingIndex >= 0) {
        items.splice(existingIndex, 1);
    } else {
        items.unshift({
            sku: item.sku,
            itemName: item.itemName,
            brand: item.brand,
            category: item.category,
            description: item.description,
            unitPrice: item.unitPrice,
            listPrice: item.listPrice,
            discountPercent: item.discountPercent,
            currencyCode: item.currencyCode,
            pictureUrls: item.pictureUrls || []
        });
    }
    saveWishlistItems(items);
    return existingIndex < 0;
}

function syncSessionChrome() {
    document.querySelectorAll(".header-actions a").forEach((link) => {
        const href = link.getAttribute("href");
        if (href === "/signin.html" || (href === "/account.html" && link.textContent.trim().startsWith("Account"))) {
            if (state.token) {
                link.textContent = "Account";
                link.setAttribute("href", "#account-menu");
            } else {
                link.textContent = "Sign in";
                link.setAttribute("href", `/signin.html?returnTo=${encodeURIComponent(currentReturnTo())}`);
                link.style.display = "";
            }
            return;
        }
        if (href === "/account.html") {
            if (state.token) {
                link.style.display = "none";
            } else {
                link.textContent = "Account";
                link.style.display = "";
            }
        }
    });
    document.querySelectorAll("a[data-auth-link='true']").forEach((link) => {
        if (state.token) {
            link.innerHTML = `<span class="header-stack"><strong>Hi, ${escapeHtml(state.username || "there")}</strong><span>Account</span></span>`;
            link.setAttribute("href", "#account-menu");
        } else {
            link.innerHTML = `<span class="header-stack"><strong>Sign in</strong><span>Account</span></span>`;
            link.setAttribute("href", `/signin.html?returnTo=${encodeURIComponent(currentReturnTo())}`);
        }
    });
    document.querySelectorAll("a[data-wishlist-link='true']").forEach((link) => {
        link.innerHTML = `<span class="header-stack"><strong>Lists</strong><span>My items</span></span>`;
        link.setAttribute("href", state.token ? "/wishlist.html" : `/signin.html?returnTo=${encodeURIComponent("/wishlist.html")}`);
    });
    attachAccountMenu();
}

function attachAccountMenu() {
    document.querySelectorAll(".header-actions").forEach((actions) => {
        const accountLink = actions.querySelector("a[data-auth-link='true']");
        let wrapper = actions.querySelector(".account-menu-wrapper");
        if (!state.token) {
            if (wrapper) wrapper.remove();
            return;
        }
        if (!accountLink) return;
        if (wrapper) wrapper.remove();
        wrapper = document.createElement("div");
        wrapper.className = "account-menu-wrapper";
        const menu = document.createElement("div");
        menu.className = "account-menu";
        menu.hidden = true;
        menu.innerHTML = `
            <a href="/account.html">Account</a>
            <button type="button" id="header-signout-button">Log out</button>
        `;
        accountLink.onclick = (event) => {
            event.preventDefault();
            menu.hidden = !menu.hidden;
        };
        menu.querySelector("#header-signout-button").addEventListener("click", () => {
            clearSession();
            window.location.href = "/index.html";
        });
        wrapper.append(menu);
        accountLink.after(wrapper);
    });
}

document.addEventListener("click", (event) => {
    document.querySelectorAll(".account-menu-wrapper").forEach((wrapper) => {
        if (!wrapper.contains(event.target) && !event.target.closest?.("a[href='#account-menu']")) {
            const menu = wrapper.querySelector(".account-menu");
            if (menu) menu.hidden = true;
        }
    });
});

function sortAddresses(addresses = []) {
    return [...addresses].sort((left, right) => Number(Boolean(right.defaultAddress)) - Number(Boolean(left.defaultAddress)));
}

function sortPaymentMethods(methods = []) {
    return [...methods].sort((left, right) => Number(Boolean(right.defaultMethod)) - Number(Boolean(left.defaultMethod)));
}

function addressLabel(address) {
    return [address.label || address.addressType || "Address", address.defaultAddress ? "Default" : ""]
        .filter(Boolean)
        .join(" • ");
}

function addressText(address) {
    return [
        address.recipientName,
        address.addressLine1,
        address.addressLine2,
        [address.city, address.state, address.postalCode].filter(Boolean).join(", "),
        address.country
    ].filter(Boolean).join(", ");
}

function formatSnapshotAddress(address) {
    if (!address) return "No address stored.";
    return [
        address.recipientName,
        address.addressLine1,
        address.addressLine2,
        [address.city, address.state, address.postalCode].filter(Boolean).join(", "),
        address.country,
        address.phoneNumber
    ].filter(Boolean).join(", ");
}

function formatSnapshotSummary(address) {
    if (!address) return "No address stored.";
    const locality = [address.city, address.state, address.postalCode].filter(Boolean).join(", ");
    return [
        address.recipientName || "Saved on order",
        locality || address.country || "Location unavailable"
    ].filter(Boolean).join(" • ");
}

function formatAddressSummary(address) {
    if (!address) return "No saved location";
    return [address.city, address.state, address.postalCode].filter(Boolean).join(", ") || address.country || "Location unavailable";
}

function paymentMethodLabel(payment = {}) {
    const provider = payment.provider || "";
    const type = String(payment.paymentMethodType || "Payment method")
        .replaceAll("_", " ")
        .toLowerCase()
        .replace(/\b\w/g, (char) => char.toUpperCase());
    return provider ? `${provider} ${type}` : type;
}

function paymentMethodSummary(payment = {}) {
    return payment.maskedNumber || payment.accountToken || "Saved payment method";
}

function paymentMethodMeta(payment = {}) {
    const parts = [
        payment.cardholderName,
        payment.expiryMonth && payment.expiryYear ? `Exp ${String(payment.expiryMonth).padStart(2, "0")}/${payment.expiryYear}` : ""
    ].filter(Boolean);
    return parts.join(" • ") || "Ready for checkout";
}

function withinHours(value, hours) {
    if (!value) return false;
    return (Date.now() - new Date(value).getTime()) <= hours * 60 * 60 * 1000;
}

function withinDays(value, days) {
    if (!value) return false;
    return (Date.now() - new Date(value).getTime()) <= days * 24 * 60 * 60 * 1000;
}

function canCancelOrder(order) {
    return ["CREATED", "PAID"].includes(order?.status) && withinHours(order?.createdAt, 24);
}

function canRefundOrder(order) {
    return order?.status === "PAID" && !withinHours(order?.createdAt, 24) && withinDays(order?.paidAt || order?.createdAt, 7);
}

async function getCatalogItems() {
    if (state.itemCatalog) {
        return state.itemCatalog;
    }
    try {
        state.itemCatalog = await api.getItems();
    } catch (error) {
        state.itemCatalog = fallbackItems;
    }
    return state.itemCatalog;
}

async function getCatalogItemBySku(sku) {
    const items = await getCatalogItems();
    return items.find((item) => item.sku === sku) || fallbackItems.find((item) => item.sku === sku) || null;
}

function populatePaymentOptions(account) {
    const select = document.getElementById("checkout-payment-method");
    const addLink = document.getElementById("checkout-add-payment-link");
    if (!select) return;
    const methods = sortPaymentMethods(account?.paymentMethods || []);
    if (!methods.length) {
        select.innerHTML = `<option value="">Choose a payment method</option><option value="CREDIT_CARD">Credit card</option>`;
        if (addLink) addLink.hidden = false;
        return;
    }
    select.innerHTML = `<option value="">Choose a payment method</option>`;
    methods.forEach((method) => {
        const option = document.createElement("option");
        option.value = method.paymentMethodType || "CREDIT_CARD";
        option.textContent = [method.defaultMethod ? "Default" : "", method.provider || method.paymentMethodType, method.maskedNumber].filter(Boolean).join(" • ");
        select.appendChild(option);
    });
    select.value = methods[0].paymentMethodType || "";
    if (addLink) addLink.hidden = true;
}

async function apiRequest(path, options = {}) {
    const headers = new Headers(options.headers || {});
    if (state.token) headers.set("Authorization", `Bearer ${state.token}`);
    if (options.body && !headers.has("Content-Type")) headers.set("Content-Type", "application/json");

    const response = await fetch(path, { ...options, headers });
    if (!response.ok) {
        let message = `${response.status} ${response.statusText}`;
        try {
            const payload = await response.json();
            message = payload.message || payload.details || message;
        } catch (error) {}
        throw new Error(message);
    }
    if (response.status === 204) return null;
    const contentType = response.headers.get("content-type") || "";
    if (contentType.includes("application/json")) {
        return response.json();
    }
    return response.text();
}

const api = {
    signin(payload) {
        return apiRequest("/api/v1/auth/signin", { method: "POST", body: JSON.stringify(payload) });
    },
    signup(payload) {
        return apiRequest("/api/v1/auth/signup", { method: "POST", body: JSON.stringify(payload) });
    },
    getMyAccount() {
        return apiRequest("/api/v1/shopping/accounts/me");
    },
    getAccountByIdentity(identity) {
        return apiRequest(`/api/v1/shopping/accounts/lookup/${encodeURIComponent(identity)}`);
    },
    getAccountByUsername(username) {
        return apiRequest(`/api/v1/shopping/accounts/username/${encodeURIComponent(username)}`);
    },
    getItems() {
        return apiRequest("/api/v1/shopping/items");
    },
    getItemBySku(sku) {
        return apiRequest(`/api/v1/shopping/items/sku/${encodeURIComponent(sku)}`);
    },
    getCart(customerId) {
        return apiRequest(`/api/v1/shopping/carts/${customerId}`);
    },
    checkoutCart(customerId) {
        return apiRequest(`/api/v1/shopping/carts/${customerId}/checkout`, { method: "POST" });
    },
    addToCart(customerId, payload) {
        return apiRequest(`/api/v1/shopping/carts/${customerId}/items`, { method: "POST", body: JSON.stringify(payload) });
    },
    updateCartItem(customerId, itemId, payload) {
        return apiRequest(`/api/v1/shopping/carts/${customerId}/items/${encodeURIComponent(itemId)}`, { method: "PUT", body: JSON.stringify(payload) });
    },
    removeCartItem(customerId, itemId) {
        return apiRequest(`/api/v1/shopping/carts/${customerId}/items/${encodeURIComponent(itemId)}`, { method: "DELETE" });
    },
    getAccount(accountId) {
        return apiRequest(`/api/v1/shopping/accounts/${accountId}`);
    },
    updateAccount(accountId, payload) {
        return apiRequest(`/api/v1/shopping/accounts/${accountId}`, { method: "PUT", body: JSON.stringify(payload) });
    },
    createOrder(payload) {
        return apiRequest("/api/v1/shopping/orders", { method: "POST", body: JSON.stringify(payload) });
    },
    getOrder(orderNumber) {
        return apiRequest(`/api/v1/shopping/orders/${encodeURIComponent(orderNumber)}`);
    },
    getOrdersByCustomer(customerId) {
        return apiRequest(`/api/v1/shopping/orders/customers/${customerId}`);
    },
    cancelOrder(orderNumber, payload) {
        return apiRequest(`/api/v1/shopping/orders/${encodeURIComponent(orderNumber)}/cancel`, { method: "POST", body: JSON.stringify(payload) });
    },
    submitPayment(payload) {
        return apiRequest("/api/v1/shopping/payments", { method: "POST", body: JSON.stringify(payload) });
    },
    updatePayment(paymentNumber, payload) {
        return apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}`, { method: "PUT", body: JSON.stringify(payload) });
    },
    cancelPaidPayment(paymentNumber, payload) {
        return apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}/cancel`, { method: "POST", body: JSON.stringify(payload) });
    },
    refundPayment(paymentNumber, payload) {
        return apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}/refund`, { method: "POST", body: JSON.stringify(payload) });
    },
    getPayment(paymentNumber) {
        return apiRequest(`/api/v1/shopping/payments/${encodeURIComponent(paymentNumber)}`);
    }
};

function bindGlobalSearch() {
    const form = document.getElementById("global-search-form");
    const input = document.getElementById("global-search");
    if (!form || !input) return;
    const button = form.querySelector("button");
    if (button) {
        button.innerHTML = "&#128269;";
        button.setAttribute("aria-label", "Search");
    }
    form.addEventListener("submit", (event) => {
        event.preventDefault();
        const value = input.value.trim();
        window.location.href = value ? `/index.html?q=${encodeURIComponent(value)}` : "/index.html";
    });
}

function inventoryTag(item) {
    const available = item.inventory?.availableQuantity ?? 0;
    if (item.inventory && available <= 0) return { text: "Out of stock", className: "tag out" };
    if (item.inventory && available < 8) return { text: `Only ${available} left`, className: "tag low" };
    return { text: "In stock", className: "tag" };
}

function getGuestCatalog() {
    const term = (query.get("q") || "").trim().toLowerCase();
    if (!term) {
        return fallbackItems;
    }
    const filtered = fallbackItems.filter((item) =>
        [item.itemName, item.brand, item.category, item.description, item.sku]
            .filter(Boolean)
            .some((value) => value.toLowerCase().includes(term))
    );
    return filtered.length ? filtered : fallbackItems;
}

async function loadCartSilently() {
    if (!state.token) return null;
    try {
        state.cart = await api.getCart(state.customerId);
        return state.cart;
    } catch (error) {
        state.cart = { items: [] };
        return state.cart;
    }
}

function cartUnitCount(cart) {
    return (cart?.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0);
}

async function resolveSignedInAccount() {
    if (!state.token || !state.username) {
        state.account = null;
        return null;
    }
    try {
        const account = await api.getMyAccount();
        state.account = account;
        state.customerId = Number(account.id);
        saveSession();
        return account;
    } catch (error) {
        state.account = null;
        return null;
    }
}

function updateHomeCartPill() {
    const headerCartPill = document.getElementById("header-cart-pill");
    if (!headerCartPill) return;
    const count = state.token ? cartUnitCount(state.cart) : 0;
    headerCartPill.textContent = `${count} item${count === 1 ? "" : "s"}`;
}

function buildTile(item) {
    const wrapper = document.createElement("article");
    wrapper.className = "tile-card";
    const tag = inventoryTag(item);
    const image = item.pictureUrls && item.pictureUrls.length ? `<img src="${escapeHtml(item.pictureUrls[0])}" alt="${escapeHtml(item.itemName || item.sku)}">` : "";
    const priceMarkup = item.listPrice && Number(item.listPrice) > Number(item.unitPrice || 0)
        ? `<div class="price-stack"><strong class="tile-price">${formatMoney(item.unitPrice, item.currencyCode || "USD")}</strong><span class="list-price">${formatMoney(item.listPrice, item.currencyCode || "USD")}</span></div>`
        : `<strong class="tile-price">${formatMoney(item.unitPrice, item.currencyCode || "USD")}</strong>`;
    const discountMarkup = item.discountPercent ? `<span class="member-badge">${item.discountPercent}% off</span>` : "";

    wrapper.innerHTML = `
        <a href="/product.html?sku=${encodeURIComponent(item.sku)}">
            <div class="tile-image">
                <button class="wishlist-heart" type="button" aria-label="Save item">${isWishlisted(item.sku) ? "♥" : "♡"}</button>
                ${image}
                <div class="tile-fallback"${image ? ' style="display:none;"' : ""}>No image</div>
            </div>
        </a>
        <div class="tile-body">
            <p class="eyebrow">${escapeHtml(item.brand || item.category || "Featured")}</p>
            <a href="/product.html?sku=${encodeURIComponent(item.sku)}"><h3 class="product-title">${escapeHtml(item.itemName || item.sku)}</h3></a>
            <p class="tile-meta">${escapeHtml(item.description || item.category || "Everyday value")}</p>
            ${priceMarkup}
            ${discountMarkup}
            <div class="tile-footer">
                <span class="${tag.className}">${escapeHtml(tag.text)}</span>
                <button class="tile-cta primary" type="button">Add</button>
            </div>
        </div>
    `;
    const heartButton = wrapper.querySelector(".wishlist-heart");
    heartButton.addEventListener("click", (event) => {
        event.preventDefault();
        event.stopPropagation();
        if (!state.token) {
            window.location.href = `/signin.html?returnTo=${encodeURIComponent(currentReturnTo())}`;
            return;
        }
        const active = toggleWishlist(item);
        heartButton.textContent = active ? "♥" : "♡";
    });
    const addButton = wrapper.querySelector(".tile-cta.primary");
    addButton.addEventListener("click", async (event) => {
        event.preventDefault();
        event.stopPropagation();
        if (!state.token) {
            window.location.href = `/signin.html?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`;
            return;
        }
        if (!state.account) {
            await resolveSignedInAccount();
        }
        if (!state.customerId || !state.account) {
            clearSession();
            window.location.href = `/signin.html?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`;
            return;
        }
        try {
            const cart = await api.addToCart(state.customerId, {
                itemId: item.id || item.sku,
                sku: item.sku,
                itemName: item.itemName,
                upc: item.upc,
                quantity: 1,
                unitPrice: item.unitPrice
            });
            state.cart = cart;
            updateHomeCartPill();
            addButton.textContent = "Added";
            window.setTimeout(() => {
                addButton.textContent = "Add";
            }, 1000);
        } catch (error) {
            if (String(error.message).includes("401")) {
                clearSession();
                window.location.href = `/signin.html?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`;
                return;
            }
            window.location.href = `/product.html?sku=${encodeURIComponent(item.sku)}`;
        }
    });
    return wrapper;
}

async function initHome() {
    const term = (query.get("q") || "").trim();
    const catalogState = document.getElementById("home-catalog-state");
    const catalogMeta = document.getElementById("home-catalog-meta");
    const grid = document.getElementById("home-product-grid");
    const dealsGrid = document.getElementById("home-deals-grid");
    const dealsState = document.getElementById("home-deals-state");
    const dealsMeta = document.getElementById("home-deals-meta");
    const categoryNav = document.getElementById("home-category-nav");
    const categorySections = document.getElementById("home-category-sections");
    const categoryMeta = document.getElementById("home-category-meta");
    const hero = document.getElementById("home-hero");
    const dealsShelf = document.getElementById("home-deals-shelf");
    const categoryAnchor = document.getElementById("home-category-section-anchor");
    const featuredHeading = document.querySelector("#home-featured-shelf h2");
    const featuredHelper = document.querySelector("#home-featured-shelf .helper");

    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
        await loadCartSilently();
    }
    updateHomeCartPill();
    let filtered;
    let usingPreviewCatalog = false;
    try {
        const items = await api.getItems();
        filtered = term
            ? items.filter((item) =>
                [item.itemName, item.brand, item.category, item.description, item.sku]
                    .filter(Boolean)
                    .some((value) => value.toLowerCase().includes(term.toLowerCase()))
            )
            : items;
        if (!filtered.length) {
            filtered = getGuestCatalog();
        }
    } catch (error) {
        filtered = getGuestCatalog();
        usingPreviewCatalog = true;
        catalogState.textContent = "";
    }

    catalogMeta.textContent = `${filtered.length} items`;
    if (term) {
        hero.hidden = true;
        dealsShelf.hidden = true;
        categoryAnchor.hidden = true;
        featuredHeading.textContent = `Results for "${term}"`;
        featuredHelper.textContent = `${filtered.length} matching items`;
    } else {
        hero.hidden = false;
        dealsShelf.hidden = false;
        categoryAnchor.hidden = false;
        featuredHeading.textContent = "Trending now";
        featuredHelper.textContent = "Shop top items across grocery, electronics, home, and more.";
    }
    catalogState.hidden = filtered.length > 0;
    if (!filtered.length) {
        catalogState.hidden = false;
        catalogState.textContent = term ? "No products matched your search." : "No products are available right now.";
    }
    grid.hidden = false;
    grid.innerHTML = "";
    filtered.forEach((item) => grid.appendChild(buildTile(item)));

    const deals = [...filtered]
        .filter((item) => Number(item.discountPercent || 0) > 0)
        .sort((left, right) => Number(right.discountPercent || 0) - Number(left.discountPercent || 0))
        .slice(0, 8);
    dealsMeta.textContent = `${deals.length} deals`;
    if (!deals.length) {
        dealsGrid.hidden = true;
        dealsState.hidden = false;
        dealsState.textContent = "No deals are available right now.";
    } else {
        dealsState.hidden = true;
        dealsGrid.hidden = false;
        dealsGrid.innerHTML = "";
        deals.forEach((item) => dealsGrid.appendChild(buildDealTile(item)));
    }

    renderCategorySections(filtered, categoryNav, categorySections, categoryMeta);
}

function buildDealTile(item) {
    const card = buildTile(item);
    card.classList.add("deal-tile");
    const ribbon = document.createElement("span");
    ribbon.className = "deal-ribbon";
    ribbon.textContent = `${item.discountPercent}% off`;
    card.prepend(ribbon);
    return card;
}

function renderCategorySections(items, navTarget, sectionTarget, metaTarget) {
    navTarget.innerHTML = `<a class="category-jump-link" href="#home-featured-shelf">Featured</a><a class="category-jump-link" href="#home-deals-shelf">Deals</a>`;
    sectionTarget.innerHTML = "";
    const byCategory = new Map();
    items.forEach((item) => {
        const category = item.category || "Featured";
        const group = byCategory.get(category) || [];
        group.push(item);
        byCategory.set(category, group);
    });

    const lanes = [...byCategory.entries()]
        .sort((left, right) => left[0].localeCompare(right[0]));

    if (!lanes.length) {
        metaTarget.textContent = "No categories available";
        sectionTarget.innerHTML = `<div class="message-box">No categories are available yet.</div>`;
        return;
    }

        metaTarget.textContent = `${lanes.length} departments`;
    lanes.forEach(([category, products]) => {
        const anchorId = `category-${slugify(category)}`;
        const navLink = document.createElement("a");
        navLink.className = "category-jump-link";
        navLink.href = `#${anchorId}`;
        navLink.textContent = category;
        navTarget.appendChild(navLink);

        const lane = document.createElement("article");
        lane.className = "category-lane";
        lane.id = anchorId;
        lane.innerHTML = `
            <div class="category-lane-head">
                <div>
                    <p class="eyebrow">Department</p>
                    <h3>${escapeHtml(category)}</h3>
                </div>
                <span class="tag">Shop</span>
            </div>
        `;

        const laneProducts = document.createElement("div");
        laneProducts.className = "category-lane-products";
        products.slice(0, 6).forEach((item) => laneProducts.appendChild(buildTile(item)));
        lane.appendChild(laneProducts);
        sectionTarget.appendChild(lane);
    });
}

async function initSignin() {
    const form = document.getElementById("signin-form");
    const message = document.getElementById("signin-message");
    syncSessionChrome();

    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const accountOrEmail = document.getElementById("signin-account").value.trim();
        const password = document.getElementById("signin-password").value;
        try {
            const response = await api.signin({ accountOrEmail, password });
            state.token = response.accessToken || "";
            state.username = response.username || accountOrEmail;
            state.expiresIn = response.expiresIn || "";
            await resolveSignedInAccount();
            saveSession();
            syncSessionChrome();
            message.textContent = state.account
                ? `Welcome back, ${state.account.fullName || state.username}.`
                : "Signed in successfully.";
            const returnTo = query.get("returnTo");
            window.setTimeout(() => {
                window.location.href = returnTo || "/index.html";
            }, 250);
        } catch (error) {
            message.textContent = `Sign-in failed: ${error.message}`;
        }
    });

}

async function initSignup() {
    const form = document.getElementById("signup-form");
    const message = document.getElementById("signup-message");
    message.hidden = true;
    syncSessionChrome();
    updateHomeCartPill();
    form.addEventListener("submit", async (event) => {
        event.preventDefault();
        const validationError = validateSignupForm();
        if (validationError) {
            message.hidden = false;
            message.textContent = validationError;
            return;
        }
        try {
            await api.signup({
                name: document.getElementById("signup-name").value.trim(),
                account: document.getElementById("signup-account").value.trim(),
                email: document.getElementById("signup-email").value.trim(),
                password: document.getElementById("signup-password").value,
                phoneNumber: document.getElementById("signup-phone").value.trim(),
                addressLine1: document.getElementById("signup-line1").value.trim(),
                addressLine2: document.getElementById("signup-line2").value.trim(),
                city: document.getElementById("signup-city").value.trim(),
                state: document.getElementById("signup-state").value.trim(),
                postalCode: document.getElementById("signup-postal").value.trim(),
                country: document.getElementById("signup-country").value.trim()
            });
            message.hidden = false;
            message.textContent = "Account created. Redirecting to sign in...";
            window.setTimeout(() => {
                window.location.href = `/signin.html?returnTo=${encodeURIComponent("/account.html")}`;
            }, 700);
        } catch (error) {
            message.hidden = false;
            message.textContent = `Unable to create account: ${error.message}`;
        }
    });
}

function isNumeric(value) {
    return /^\d+$/.test(String(value || "").trim());
}

function validateSignupForm() {
    const name = document.getElementById("signup-name").value.trim();
    const username = document.getElementById("signup-account").value.trim();
    const email = document.getElementById("signup-email").value.trim();
    const password = document.getElementById("signup-password").value;
    if (!name || !username || !email || !password) {
        return "Name, username, email, and password are required.";
    }
    if (!/^[A-Za-z0-9._-]{3,30}$/.test(username)) {
        return "Username must be 3-30 characters and use letters, numbers, dots, dashes, or underscores.";
    }
    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
        return "Enter a valid email address.";
    }
    if (password.length < 6) {
        return "Password must be at least 6 characters.";
    }
    const postalCode = document.getElementById("signup-postal").value.trim();
    if (postalCode && !/^[A-Za-z0-9 -]{4,10}$/.test(postalCode)) {
        return "Enter a valid postal code.";
    }
    return "";
}

function validateAddressEditor() {
    if (!document.getElementById("address-recipient").value.trim()
        || !document.getElementById("address-line1").value.trim()
        || !document.getElementById("address-city").value.trim()
        || !document.getElementById("address-state").value.trim()
        || !document.getElementById("address-postal").value.trim()) {
        return "Recipient, address, city, state, and postal code are required.";
    }
    return "";
}

function validatePaymentEditor() {
    const cardNumber = document.getElementById("payment-masked").value.trim().replace(/\s+/g, "");
    const cvv = document.getElementById("payment-token").value.trim();
    const month = Number(document.getElementById("payment-expiry-month").value || 0);
    const year = Number(document.getElementById("payment-expiry-year").value || 0);
    if (!document.getElementById("payment-provider").value.trim() || !document.getElementById("payment-cardholder").value.trim()) {
        return "Provider and cardholder are required.";
    }
    if (!isNumeric(cardNumber) || cardNumber.length < 12 || cardNumber.length > 19) {
        return "Card number must be 12-19 digits.";
    }
    if (!isNumeric(cvv) || cvv.length < 3 || cvv.length > 4) {
        return "CVV must be 3 or 4 digits.";
    }
    if (month < 1 || month > 12) {
        return "Expiry month must be between 1 and 12.";
    }
    if (year < new Date().getFullYear()) {
        return "Enter a valid expiry year.";
    }
    return "";
}

async function initProduct() {
    const sku = query.get("sku");
    const stateBox = document.getElementById("product-state");
    const content = document.getElementById("product-detail-content");
    const addButton = document.getElementById("product-add-button");
    const wishlistButton = document.getElementById("product-wishlist-button");
    const qtyReadout = document.getElementById("product-qty-readout");
    const minus = document.getElementById("product-qty-minus");
    const plus = document.getElementById("product-qty-plus");
    const message = document.getElementById("product-message");
    const cartUnits = document.getElementById("product-cart-units");
    const fulfillmentTitle = document.getElementById("product-fulfillment-title");
    const fulfillmentDetail = document.getElementById("product-fulfillment-detail");
    let quantity = 1;
    let lastCartUnitsLabel = "";

    function syncProductAddButton(item = null, forceAdded = false) {
        if (!addButton) return;
        const inCart = forceAdded || (item && (state.cart?.items || []).some((cartItem) =>
            String(cartItem.sku || "") === String(item.sku || "") ||
            String(cartItem.itemId || "") === String(item.id || item.sku || "")
        ));
        addButton.textContent = inCart ? "Added" : "Add to cart";
        addButton.dataset.added = inCart ? "true" : "false";
    }

    function syncProductChromeFromCartUnits() {
        const label = (cartUnits?.textContent || "0 items").trim();
        const headerCartPill = document.getElementById("header-cart-pill");
        if (headerCartPill) {
            headerCartPill.textContent = label;
        }
        return label;
    }

    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
        await loadCartSilently();
    }
    updateHomeCartPill();
    cartUnits.textContent = `${cartUnitCount(state.cart)} item${cartUnitCount(state.cart) === 1 ? "" : "s"}`;
    lastCartUnitsLabel = syncProductChromeFromCartUnits();
    updateHomeCartPill();

    if (!sku) {
        stateBox.textContent = "No product selected.";
        return;
    }

    try {
        let item;
        try {
            item = await api.getItemBySku(sku);
        } catch (error) {
            item = fallbackItems.find((candidate) => candidate.sku === sku);
            if (!item) {
                throw error;
            }
            stateBox.textContent = "";
        }
        const tag = inventoryTag(item);
        const image = item.pictureUrls && item.pictureUrls.length ? item.pictureUrls[0] : "";
        const discountBadge = document.getElementById("product-discount-badge");
        document.getElementById("product-brand").textContent = item.brand || item.category || "Featured";
        document.getElementById("product-name").textContent = item.itemName || item.sku;
        document.getElementById("product-meta").textContent = item.category || "";
        const priceNode = document.getElementById("product-price");
        priceNode.innerHTML = item.listPrice && Number(item.listPrice) > Number(item.unitPrice || 0)
            ? `${escapeHtml(formatMoney(item.unitPrice, item.currencyCode || "USD"))} <span class="list-price" style="font-size:1rem; margin-left:8px;">${escapeHtml(formatMoney(item.listPrice, item.currencyCode || "USD"))}</span>`
            : escapeHtml(formatMoney(item.unitPrice, item.currencyCode || "USD"));
        if (item.discountPercent) {
            discountBadge.hidden = false;
            discountBadge.textContent = `${item.discountPercent}% off`;
        } else {
            discountBadge.hidden = true;
        }
        document.getElementById("product-description").textContent = item.description || "No description provided.";
        document.getElementById("product-inventory-tag").textContent = tag.text;
        document.getElementById("product-inventory-tag").className = tag.className;
        document.getElementById("product-buybox-price").textContent = formatMoney(item.unitPrice, item.currencyCode || "USD");
        document.getElementById("product-buybox-inventory").textContent = tag.text;
        document.getElementById("product-buybox-inventory").className = tag.className;
        document.getElementById("product-image").src = image;
        document.getElementById("product-image").alt = item.itemName || item.sku;
        if (image) document.getElementById("product-image-fallback").style.display = "none";
        stateBox.hidden = true;
        content.hidden = false;
        syncProductAddButton(item);
        syncProductChromeFromCartUnits();

        if (cartUnits && addButton) {
            const observer = new MutationObserver(() => {
                const nextLabel = syncProductChromeFromCartUnits();
                if (nextLabel !== lastCartUnitsLabel) {
                    addButton.textContent = "Added";
                    addButton.dataset.added = "true";
                    lastCartUnitsLabel = nextLabel;
                }
            });
            observer.observe(cartUnits, { childList: true, characterData: true, subtree: true });
        }

        minus.addEventListener("click", () => {
            quantity = Math.max(1, quantity - 1);
            qtyReadout.textContent = String(quantity);
        });
        plus.addEventListener("click", () => {
            quantity = Math.min(20, quantity + 1);
            qtyReadout.textContent = String(quantity);
        });
        addButton.addEventListener("click", async () => {
            if (!state.token) {
                message.textContent = "Sign in before adding this item to cart.";
                window.setTimeout(() => {
                    window.location.href = `/signin.html?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`;
                }, 700);
                return;
            }
            if (!state.account) {
                await resolveSignedInAccount();
            }
            if (!state.customerId || !state.account) {
                clearSession();
                window.location.href = `/signin.html?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`;
                return;
            }
            try {
                const selectedFulfillment = document.querySelector(".delivery-choice.active")?.dataset.fulfillment === "pickup" ? "PICKUP" : "SHIPPING";
                await api.addToCart(state.customerId, {
                    itemId: composeCartItemId(item.sku, selectedFulfillment),
                    sku: item.sku,
                    itemName: item.itemName,
                    upc: item.upc,
                    quantity,
                    unitPrice: item.unitPrice
                });
                state.cart = await api.getCart(state.customerId);
                const nextCount = cartUnitCount(state.cart);
                cartUnits.textContent = `${nextCount} item${nextCount === 1 ? "" : "s"}`;
                message.textContent = `${item.itemName || item.sku} added to cart.`;
                updateHomeCartPill();
                syncProductChromeFromCartUnits();
                syncProductAddButton(item, true);
            } catch (error) {
                if (String(error.message).includes("401")) {
                    clearSession();
                    window.location.href = `/signin.html?returnTo=${encodeURIComponent(window.location.pathname + window.location.search)}`;
                    return;
                }
                message.textContent = `Unable to add item: ${error.message}`;
            }
        });
        wishlistButton.textContent = isWishlisted(item.sku) ? "Added to list" : "Add to list";
        document.querySelectorAll(".delivery-choice").forEach((button) => {
            button.addEventListener("click", () => {
                document.querySelectorAll(".delivery-choice").forEach((node) => node.classList.remove("active"));
                button.classList.add("active");
                switch (button.dataset.fulfillment) {
                    case "pickup":
                        fulfillmentTitle.textContent = "Pickup";
                        fulfillmentDetail.textContent = `Pick up today at ${DEMO_PICKUP_LOCATION}.`;
                        break;
                    default:
                        fulfillmentTitle.textContent = "Shipping";
                        fulfillmentDetail.textContent = "Ships to your saved address at checkout.";
                        break;
                }
            });
        });
        wishlistButton.addEventListener("click", () => {
            if (!state.token) {
                window.location.href = `/signin.html?returnTo=${encodeURIComponent(currentReturnTo())}`;
                return;
            }
            const active = toggleWishlist(item);
            wishlistButton.textContent = active ? "Added to list" : "Add to list";
        });
    } catch (error) {
        stateBox.textContent = `Unable to load product: ${error.message}`;
    }
}

async function initCart() {
    const message = document.getElementById("cart-page-message");
    const itemsNode = document.getElementById("cart-page-items");
    const unitCount = document.getElementById("cart-page-unit-count");
    const totalNode = document.getElementById("cart-page-total");
    const refresh = document.getElementById("cart-refresh-button");
    const checkoutButton = document.getElementById("cart-checkout-button");
    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
    }

    async function render() {
        if (!state.token) {
            window.location.href = `/signin.html?returnTo=${encodeURIComponent("/cart.html")}`;
            return;
        }
        try {
            state.cart = await api.getCart(state.customerId);
            const items = state.cart.items || [];
            updateHomeCartPill();
            const selectedIds = normalizeSelectedCartItemsForCart(items);
            itemsNode.innerHTML = "";
            if (!items.length) {
                message.textContent = "Your cart is empty.";
                unitCount.textContent = "0";
                totalNode.textContent = "$0.00";
                return;
            }
            message.textContent = `${items.length} item${items.length === 1 ? "" : "s"} in your cart.`;
            const groups = groupCartItemsByFulfillment(items);
            renderCartGroup("Ship to home", groups.shipping, selectedIds);
            renderCartGroup("Pickup", groups.pickup, selectedIds, DEMO_PICKUP_LOCATION);
            itemsNode.querySelectorAll("input[type='checkbox'][data-item-id]").forEach((checkbox) => {
                checkbox.addEventListener("change", () => {
                    const nextIds = Array.from(itemsNode.querySelectorAll("input[type='checkbox'][data-item-id]:checked"))
                        .map((node) => normalizeCartItemId(node.getAttribute("data-item-id")))
                        .filter(Boolean);
                    saveSelectedCartItems(nextIds);
                    updateCartSummary(items);
                });
            });
            updateCartSummary(items);
        } catch (error) {
            if (String(error.message).includes("401")) {
                clearSession();
                window.location.href = `/signin.html?returnTo=${encodeURIComponent("/cart.html")}`;
                return;
            }
            message.textContent = `Unable to load cart: ${error.message}`;
        }
    }

    function renderCartGroup(title, items, selectedIds, subtitle = "") {
        if (!items.length) return;
        const units = countUnits(items);
        const total = items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0);
        const section = document.createElement("section");
        section.className = "cart-fulfillment-group";
        section.innerHTML = `
            <div class="cart-group-head">
                <div class="stack-tight">
                    <p class="eyebrow">${escapeHtml(title)}</p>
                    <strong>${units} item${units === 1 ? "" : "s"}</strong>
                    ${subtitle ? `<div class="muted">${escapeHtml(subtitle)}</div>` : ""}
                </div>
                <div class="cart-group-summary">
                    <span>${escapeHtml(formatMoney(total))}</span>
                    <span class="cart-group-chip">${items.length} line item${items.length === 1 ? "" : "s"}</span>
                </div>
            </div>
        `;
        items.forEach((item) => {
            const checked = selectedIds.has(normalizeCartItemId(item.itemId));
            const row = document.createElement("article");
            row.className = "cart-row";
            row.innerHTML = `
                <div class="cart-row-main">
                    <div class="cart-item-block">
                        <label class="cart-select">
                            <input type="checkbox" ${checked ? "checked" : ""} data-item-id="${escapeHtml(item.itemId)}">
                            <span>Select item</span>
                        </label>
                        <div class="cart-item-copy">
                            <strong>${escapeHtml(item.itemName || item.sku)}</strong>
                            <div class="muted">${item.quantity} × ${formatMoney(item.unitPrice)}</div>
                            <div class="fulfillment-inline-meta">
                                <span class="fulfillment-badge ${isPickupItem(item) ? "pickup" : "shipping"}">${isPickupItem(item) ? "Pickup" : "Shipping"}</span>
                                <span class="muted">${isPickupItem(item) ? `Pick up at ${escapeHtml(DEMO_PICKUP_LOCATION)}` : "Ships to your saved address"}</span>
                            </div>
                        </div>
                    </div>
                    <strong>${formatMoney(Number(item.quantity || 0) * Number(item.unitPrice || 0))}</strong>
                </div>
            `;
            const actions = document.createElement("div");
            actions.className = "inline-actions";
            const minus = document.createElement("button");
            minus.className = "qty-button";
            minus.type = "button";
            minus.textContent = "-1";
            minus.onclick = async () => updateQuantity(item, Number(item.quantity || 0) - 1);
            const plus = document.createElement("button");
            plus.className = "qty-button";
            plus.type = "button";
            plus.textContent = "+1";
            plus.onclick = async () => updateQuantity(item, Number(item.quantity || 0) + 1);
            const remove = document.createElement("button");
            remove.className = "secondary-button";
            remove.type = "button";
            remove.textContent = "Remove";
            remove.onclick = async () => updateQuantity(item, 0);
            actions.append(minus, plus, remove);
            row.appendChild(actions);
            section.appendChild(row);
        });
        itemsNode.appendChild(section);
    }

    function updateCartSummary(items) {
        const selectedIds = normalizeSelectedCartItemsForCart(items);
        const selectedItems = items.filter((item) => selectedIds.has(normalizeCartItemId(item.itemId)));
        const units = selectedItems.reduce((sum, item) => sum + Number(item.quantity || 0), 0);
        const total = selectedItems.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0);
        unitCount.textContent = String(units);
        totalNode.textContent = formatMoney(total);
        checkoutButton.disabled = !selectedItems.length;
    }

    async function updateQuantity(item, quantity) {
        try {
            if (quantity <= 0) {
                state.cart = await api.removeCartItem(state.customerId, item.itemId);
            } else {
                state.cart = await api.updateCartItem(state.customerId, item.itemId, {
                    itemId: item.itemId,
                    sku: item.sku,
                    itemName: item.itemName,
                    upc: item.upc,
                    quantity,
                    unitPrice: item.unitPrice
                });
            }
            await render();
        } catch (error) {
            message.textContent = `Unable to update cart: ${error.message}`;
        }
    }

    refresh.addEventListener("click", render);
    checkoutButton.addEventListener("click", () => {
        if (!getSelectedCartItems().length) {
            message.textContent = "Select at least one item to continue.";
            return;
        }
        window.location.href = "/checkout.html";
    });
    await render();
}

function readAddress(prefix) {
    return {
        recipientName: document.getElementById(`${prefix}-recipient`).value.trim(),
        addressLine1: document.getElementById(`${prefix}-line1`).value.trim(),
        addressLine2: document.getElementById(`${prefix}-line2`).value.trim(),
        city: document.getElementById(`${prefix}-city`).value.trim(),
        state: document.getElementById(`${prefix}-state`).value.trim(),
        postalCode: document.getElementById(`${prefix}-postal`).value.trim(),
        country: document.getElementById(`${prefix}-country`).value.trim(),
        phoneNumber: document.getElementById(`${prefix}-phone`).value.trim()
    };
}

function fillAddress(prefix, address) {
    document.getElementById(`${prefix}-recipient`).value = address.recipientName || "";
    document.getElementById(`${prefix}-line1`).value = address.addressLine1 || "";
    document.getElementById(`${prefix}-line2`).value = address.addressLine2 || "";
    document.getElementById(`${prefix}-city`).value = address.city || "";
    document.getElementById(`${prefix}-state`).value = address.state || "";
    document.getElementById(`${prefix}-postal`).value = address.postalCode || "";
    document.getElementById(`${prefix}-country`).value = address.country || "US";
    document.getElementById(`${prefix}-phone`).value = address.phoneNumber || "";
}

async function initCheckout() {
    const message = document.getElementById("checkout-page-message");
    const payNow = document.getElementById("checkout-pay-now");
    const shippingSelect = document.getElementById("checkout-shipping-address");
    const billingSelect = document.getElementById("checkout-billing-address");
    const sameAsShipping = document.getElementById("checkout-billing-same");
    const shippingPanel = document.getElementById("checkout-shipping-panel");
    const pickupPanel = document.getElementById("checkout-pickup-panel");
    const pickupLocationNode = document.getElementById("checkout-pickup-location");
    const fulfillmentGrid = document.getElementById("checkout-fulfillment-grid");
    const selection = (items = state.cart?.items || []) => normalizeSelectedCartItemsForCart(items);
    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
    }

    function renderAddressSelectors(account) {
        const addresses = sortAddresses(account.addresses || []);
        shippingSelect.innerHTML = "";
        billingSelect.innerHTML = "";
        if (!addresses.length) {
            shippingSelect.innerHTML = `<option value="">Add an address in Account</option>`;
            billingSelect.innerHTML = `<option value="">Add an address in Account</option>`;
            return;
        }
        addresses.forEach((address, index) => {
            const option = document.createElement("option");
            option.value = String(index);
            option.textContent = `${addressLabel(address)} — ${addressText(address)}`;
            shippingSelect.appendChild(option);
            billingSelect.appendChild(option.cloneNode(true));
        });
        shippingSelect.value = "0";
        billingSelect.value = "0";
        fillAddress("ship", addresses[0]);
        fillAddress("bill", addresses[0]);
    }

    function updateBillingVisibility() {
        const billingPanel = document.getElementById("checkout-billing-fields");
        billingPanel.hidden = sameAsShipping.checked;
        if (sameAsShipping.checked) {
            fillAddress("bill", readAddress("ship"));
        } else {
            syncSelectedAddress("bill", Number(billingSelect.value || 0));
        }
    }

    function syncSelectedAddress(prefix, index) {
        const addresses = sortAddresses(state.account?.addresses || []);
        const selected = addresses[index];
        if (!selected) return;
        fillAddress(prefix, selected);
        if (prefix === "ship" && sameAsShipping.checked) {
            fillAddress("bill", selected);
        }
    }

    function applyShippingRule(subtotal) {
        const shippingInput = document.getElementById("checkout-shipping");
        if (state.account?.membershipLevel === "PREMIUM") {
            shippingInput.value = "0.00";
            return;
        }
        shippingInput.value = subtotal >= 35 ? "0.00" : "6.99";
    }

    function selectedCheckoutItems(cart = state.cart) {
        const selectedIds = selection(cart?.items || []);
        return ((cart?.items || [])).filter((item) => selectedIds.has(normalizeCartItemId(item.itemId)));
    }

    function renderFulfillmentPanels(items) {
        const shippingItems = items.filter(isShippingItem);
        const pickupItems = items.filter(isPickupItem);
        shippingPanel.hidden = shippingItems.length === 0;
        pickupPanel.hidden = pickupItems.length === 0;
        if (pickupLocationNode && pickupItems.length) {
            pickupLocationNode.textContent = DEMO_PICKUP_LOCATION;
        }
    }

    function renderCheckoutFulfillmentSummary(items, subtotal, shipping) {
        const fulfillment = fulfillmentSummary(items);
        const cards = [
            fulfillment.shippingItems.length
                ? `<div class="order-detail-card">
                        <strong>Shipping items</strong>
                        <div class="order-detail-row">${escapeHtml(formatSnapshotSummary(readAddress("ship")))}</div>
                        <div class="muted">${fulfillment.shippingUnits} unit${fulfillment.shippingUnits === 1 ? "" : "s"} shipping</div>
                    </div>`
                : "",
            fulfillment.pickupItems.length
                ? `<div class="order-detail-card">
                        <strong>Pickup items</strong>
                        <div class="order-detail-row">${escapeHtml(DEMO_PICKUP_LOCATION)}</div>
                        <div class="muted">${fulfillment.pickupUnits} unit${fulfillment.pickupUnits === 1 ? "" : "s"} for pickup</div>
                    </div>`
                : "",
            `<div class="order-detail-card">
                <strong>Fulfillment mix</strong>
                <div class="order-detail-row">${escapeHtml(fulfillment.shippingItems.length && fulfillment.pickupItems.length ? "Shipping and pickup" : fulfillment.pickupItems.length ? "Pickup only" : "Shipping only")}</div>
            </div>`,
            `<div class="order-detail-card">
                <strong>Estimated total</strong>
                <div class="order-detail-row">${escapeHtml(formatMoney(subtotal + shipping))}</div>
            </div>`
        ].filter(Boolean).join("");
        fulfillmentGrid.innerHTML = cards;
    }

    async function loadCheckoutAccountData() {
        if (!state.token) {
            window.location.href = `/signin.html?returnTo=${encodeURIComponent("/checkout.html")}`;
            return;
        }
        try {
            const account = await api.getMyAccount();
            state.account = account;
            renderAddressSelectors(account);
            populatePaymentOptions(account);
            updateBillingVisibility();
            message.hidden = true;
            await refreshCheckoutSummary();
        } catch (error) {
            message.hidden = false;
            message.textContent = `Unable to load account: ${error.message}`;
        }
    }

    payNow.addEventListener("click", async () => {
        try {
            const selectedPaymentMethod = document.getElementById("checkout-payment-method").value;
            if (!selectedPaymentMethod) {
                message.hidden = false;
                message.textContent = "Choose a payment method.";
                return;
            }
            payNow.disabled = true;
            state.order = await createOrderFromSelection(message, selection, selectedPaymentMethod);
            message.hidden = false;
            message.textContent = "Processing order and payment...";
            const outcome = await waitForOrderOutcome(state.order.orderNumber);
            state.order = outcome.order;
            if (outcome.payment) {
                state.payment = outcome.payment;
                state.lastPaymentNumber = outcome.payment.paymentNumber;
            }
            saveSession();
            if (outcome.order.status === "PAID") {
                const purchasedItemIds = getSelectedCartItems();
                for (const itemId of purchasedItemIds) {
                    state.cart = await api.removeCartItem(state.customerId, itemId);
                }
                state.cart = await api.getCart(state.customerId);
                saveSelectedCartItems([]);
                updateHomeCartPill();
                renderCheckoutStatus();
                message.textContent = "Payment complete. Redirecting...";
                window.setTimeout(() => {
                    window.location.href = `/confirmation.html?orderNumber=${encodeURIComponent(outcome.order.orderNumber)}&paymentNumber=${encodeURIComponent(outcome.payment?.paymentNumber || "")}`;
                }, 800);
                return;
            }

            message.textContent = "Order could not be completed. Redirecting...";
            window.setTimeout(() => {
                window.location.href = `/order-failed.html?orderNumber=${encodeURIComponent(outcome.order.orderNumber)}&paymentNumber=${encodeURIComponent(outcome.payment?.paymentNumber || "")}`;
            }, 800);
        } catch (error) {
            payNow.disabled = false;
            message.hidden = false;
            message.textContent = `Unable to place order: ${error.message}`;
        }
    });

    function renderCheckoutStatus() {
        document.getElementById("checkout-order-items").textContent = String((state.order?.items || []).reduce((sum, item) => sum + Number(item.quantity || 0), 0));
        document.getElementById("checkout-order-subtotal").textContent = formatMoney(state.order?.subtotalAmount || 0, state.order?.currencyCode || "USD");
        document.getElementById("checkout-order-shipping").textContent = formatMoney(state.order?.shippingAmount || document.getElementById("checkout-shipping").value || 0, state.order?.currencyCode || "USD");
        document.getElementById("checkout-order-tax").textContent = formatMoney(state.order?.taxAmount || 0, state.order?.currencyCode || "USD");
        document.getElementById("checkout-order-discount").textContent = formatMoney(state.order?.discountAmount || 0, state.order?.currencyCode || "USD");
        document.getElementById("checkout-payment-status").textContent = displayPaymentStatus(state.payment?.paymentStatus);
        document.getElementById("checkout-order-total").textContent = formatMoney(state.order?.totalAmount || 0, state.order?.currencyCode || "USD");
    }

    async function refreshCheckoutSummary() {
        if (!state.token) return;
        const cart = await api.getCart(state.customerId);
        state.cart = cart;
        const items = selectedCheckoutItems(cart);
        const shippingItems = items.filter(isShippingItem);
        const shippingSubtotal = shippingItems.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0);
        const subtotal = items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0);
        applyShippingRule(shippingSubtotal);
        renderFulfillmentPanels(items);
        const shipping = Number(document.getElementById("checkout-shipping").value || 0);
        const tax = Number(document.getElementById("checkout-tax").value || 0);
        const catalogItems = await Promise.all(items.map((item) => getCatalogItemBySku(item.sku)));
        const discount = items.reduce((sum, item, index) => {
            const catalogItem = catalogItems[index];
            const listPrice = Number(catalogItem?.listPrice || item.unitPrice || 0);
            const unitPrice = Number(item.unitPrice || 0);
            return sum + Math.max(0, listPrice - unitPrice) * Number(item.quantity || 0);
        }, 0);
        const review = document.getElementById("checkout-review-items");
        document.getElementById("checkout-discount").value = discount.toFixed(2);
        document.getElementById("checkout-order-items").textContent = String(items.reduce((sum, item) => sum + Number(item.quantity || 0), 0));
        document.getElementById("checkout-order-subtotal").textContent = formatMoney(subtotal);
        document.getElementById("checkout-order-shipping").textContent = formatMoney(shipping);
        document.getElementById("checkout-order-tax").textContent = formatMoney(tax);
        document.getElementById("checkout-order-discount").textContent = formatMoney(discount);
        document.getElementById("checkout-order-total").textContent = formatMoney(subtotal + shipping + tax - discount);
        renderCheckoutFulfillmentSummary(items, subtotal, shipping);
        const paymentSelect = document.getElementById("checkout-payment-method");
        document.getElementById("checkout-payment-status").textContent = paymentSelect.value
            ? paymentSelect.options[paymentSelect.selectedIndex].textContent
            : "Choose a method";
        review.innerHTML = "";
        if (!items.length) {
            review.innerHTML = `<div class="message-box">Select items in your cart to review them here.</div>`;
        } else {
            [
                { title: "Shipping items", items: shippingItems, subtitle: "Ships to your saved address" },
                { title: "Pickup items", items: items.filter(isPickupItem), subtitle: DEMO_PICKUP_LOCATION }
            ].forEach((group) => {
                if (!group.items.length) return;
                const section = document.createElement("section");
                section.className = "cart-fulfillment-group";
                section.innerHTML = `
                    <div class="cart-group-head">
                        <div class="stack-tight">
                            <p class="eyebrow">${escapeHtml(group.title)}</p>
                            <strong>${countUnits(group.items)} item${countUnits(group.items) === 1 ? "" : "s"}</strong>
                            <div class="muted">${escapeHtml(group.subtitle)}</div>
                        </div>
                        <div class="cart-group-summary">
                            <span>${escapeHtml(formatMoney(group.items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0)))}</span>
                            <span class="cart-group-chip">${group.items.length} line item${group.items.length === 1 ? "" : "s"}</span>
                        </div>
                    </div>
                `;
                group.items.forEach((item) => {
                    const row = document.createElement("article");
                    row.className = "checkout-review-row";
                    row.innerHTML = `
                        <div class="stack-tight">
                            <strong>${escapeHtml(item.itemName || item.sku)}</strong>
                            <span class="muted">${escapeHtml(item.sku || "")}</span>
                            <span class="muted">Qty ${escapeHtml(item.quantity)}</span>
                            <div class="fulfillment-inline-meta">
                                <span class="fulfillment-badge ${isPickupItem(item) ? "pickup" : "shipping"}">${escapeHtml(isPickupItem(item) ? "Pickup" : "Shipping")}</span>
                                <span class="muted">${escapeHtml(isPickupItem(item) ? DEMO_PICKUP_LOCATION : "Ships to your saved address")}</span>
                            </div>
                        </div>
                        <strong>${formatMoney(Number(item.quantity || 0) * Number(item.unitPrice || 0))}</strong>
                    `;
                    section.appendChild(row);
                });
                review.appendChild(section);
            });
        }
        updateHomeCartPill();
    }

    if (state.lastOrderNumber && state.token) {
        try {
            state.order = await api.getOrder(state.lastOrderNumber);
            if (state.lastPaymentNumber) state.payment = await api.getPayment(state.lastPaymentNumber);
            renderCheckoutStatus();
        } catch (error) {}
    }
    shippingSelect.addEventListener("change", () => {
        syncSelectedAddress("ship", Number(shippingSelect.value || 0));
        refreshCheckoutSummary();
    });
    billingSelect.addEventListener("change", () => {
        syncSelectedAddress("bill", Number(billingSelect.value || 0));
    });
    sameAsShipping.addEventListener("change", () => {
        updateBillingVisibility();
        refreshCheckoutSummary();
    });
    ["ship-recipient", "ship-line1", "ship-line2", "ship-city", "ship-state", "ship-postal", "ship-country", "ship-phone"].forEach((id) => {
        const node = document.getElementById(id);
        node.addEventListener("input", () => {
            if (sameAsShipping.checked) {
                fillAddress("bill", readAddress("ship"));
            }
        });
    });
    document.getElementById("checkout-payment-method").addEventListener("change", refreshCheckoutSummary);
    document.getElementById("checkout-shipping").addEventListener("input", refreshCheckoutSummary);
    document.getElementById("checkout-tax").addEventListener("input", refreshCheckoutSummary);
    if (state.token) {
        await loadCheckoutAccountData();
        await refreshCheckoutSummary();
    }

    async function waitForOrderOutcome(orderNumber) {
        const maxAttempts = 30;
        for (let attempt = 0; attempt < maxAttempts; attempt += 1) {
            const order = await api.getOrder(orderNumber);
            let payment = null;
            if (order.paymentReference) {
                try {
                    payment = await api.getPayment(order.paymentReference);
                } catch (error) {}
            }
            if (order.status === "PAID" || order.status === "FAILED") {
                return { order, payment };
            }
            await new Promise((resolve) => window.setTimeout(resolve, 1000));
        }
        throw new Error("Order processing timed out");
    }
}

async function createOrderFromSelection(messageNode, selectionGetter, paymentMethod) {
    if (!state.token) {
        throw new Error("Sign in first.");
    }
    const cart = await api.getCart(state.customerId);
    const selectedIds = selectionGetter(cart.items || []);
    const items = (cart.items || []).filter((item) => selectedIds.has(normalizeCartItemId(item.itemId)));
    if (!items.length) {
        throw new Error("Select at least one cart item.");
    }
    const shippingItems = items.filter(isShippingItem);
    const payload = {
        customerId: state.customerId,
        currencyCode: "USD",
        taxAmount: Number(document.getElementById("checkout-tax").value || 0).toFixed(2),
        shippingAmount: shippingItems.length ? Number(document.getElementById("checkout-shipping").value || 0).toFixed(2) : "0.00",
        discountAmount: Number(document.getElementById("checkout-discount").value || 0).toFixed(2),
        shippingAddress: shippingItems.length ? readAddress("ship") : null,
        billingAddress: readAddress("bill"),
        paymentMethod,
        items: items.map((item) => ({
            itemId: item.itemId,
            sku: item.sku,
            itemName: item.itemName,
            upc: item.upc,
            quantity: item.quantity,
            unitPrice: item.unitPrice
        })),
        createRequestId: `web-order-${Date.now()}`
    };
    const order = await api.createOrder(payload);
    state.lastOrderNumber = order.orderNumber;
    saveSession();
    return order;
}

async function initConfirmation() {
    const message = document.getElementById("confirmation-message");
    const timeline = document.getElementById("confirmation-timeline");
    const fulfillmentGrid = document.getElementById("confirmation-fulfillment-grid");
    const itemGroups = document.getElementById("confirmation-item-groups");
    const orderNumber = query.get("orderNumber") || state.lastOrderNumber;
    const paymentNumber = query.get("paymentNumber") || state.lastPaymentNumber;
    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
    }
    if (!state.token || !orderNumber) {
        message.textContent = "Sign in to view order details.";
        return;
    }
    try {
        state.order = await api.getOrder(orderNumber);
        if (paymentNumber) {
            try {
                state.payment = await api.getPayment(paymentNumber);
            } catch (error) {}
        }
        document.getElementById("confirmation-order-number").textContent = state.order.orderNumber;
        document.getElementById("confirmation-order-status").textContent = displayOrderStatus(state.order.status);
        document.getElementById("confirmation-payment-status").textContent = displayPaymentStatus(state.payment?.paymentStatus);
        document.getElementById("confirmation-total").textContent = formatMoney(state.order.totalAmount || 0, state.order.currencyCode || "USD");
        message.textContent = "Thanks. Your order is confirmed.";
        timeline.innerHTML = "";
        fulfillmentGrid.innerHTML = "";
        itemGroups.innerHTML = "";
        [
            { title: "Order created", detail: formatDate(state.order.createdAt) },
            { title: "Order status", detail: state.order.status || "-" },
            { title: "Payment reference", detail: state.order.paymentReference || state.payment?.paymentNumber || "Pending" },
            { title: "Paid at", detail: formatDate(state.order.paidAt) }
        ].forEach((entry) => {
            const node = document.createElement("article");
            node.className = "timeline-item";
            node.innerHTML = `<strong>${escapeHtml(entry.title)}</strong><span>${escapeHtml(entry.detail)}</span>`;
            timeline.appendChild(node);
        });

        renderOrderFulfillmentGroups(fulfillmentGrid, itemGroups, state.order);
    } catch (error) {
        message.textContent = `Unable to load confirmation details: ${error.message}`;
    }
}

function renderOrderFulfillmentGroups(fulfillmentGrid, itemGroups, order) {
    const fulfillment = fulfillmentSummary(order.items || []);
    const detailCards = [
        fulfillment.shippingItems.length
            ? `<div class="order-detail-card">
                    <strong>Shipping</strong>
                    <div class="order-detail-row">${escapeHtml(formatSnapshotSummary(order.shippingAddress))}</div>
                    <div class="muted">${fulfillment.shippingUnits} unit${fulfillment.shippingUnits === 1 ? "" : "s"} shipping</div>
               </div>`
            : "",
        fulfillment.pickupItems.length
            ? `<div class="order-detail-card">
                    <strong>Pickup</strong>
                    <div class="order-detail-row">${escapeHtml(DEMO_PICKUP_LOCATION)}</div>
                    <div class="muted">${fulfillment.pickupUnits} unit${fulfillment.pickupUnits === 1 ? "" : "s"} for pickup</div>
               </div>`
            : "",
        `<div class="order-detail-card">
            <strong>Billing details</strong>
            <div class="order-detail-row">${escapeHtml(formatSnapshotSummary(order.billingAddress))}</div>
        </div>`,
        `<div class="order-detail-card">
            <strong>Fulfillment mix</strong>
            <div class="order-detail-row">${escapeHtml(fulfillment.shippingItems.length && fulfillment.pickupItems.length ? "Shipping and pickup" : fulfillment.pickupItems.length ? "Pickup only" : "Shipping only")}</div>
        </div>`
    ].filter(Boolean).join("");
    fulfillmentGrid.innerHTML = detailCards;

    itemGroups.innerHTML = "";
    [
        { title: "Shipping items", items: fulfillment.shippingItems, subtitle: "Ships to your saved address" },
        { title: "Pickup items", items: fulfillment.pickupItems, subtitle: DEMO_PICKUP_LOCATION }
    ].forEach((group) => {
        if (!group.items.length) return;
        const section = document.createElement("section");
        section.className = "cart-fulfillment-group";
        section.innerHTML = `
            <div class="cart-group-head">
                <div class="stack-tight">
                    <p class="eyebrow">${escapeHtml(group.title)}</p>
                    <strong>${countUnits(group.items)} item${countUnits(group.items) === 1 ? "" : "s"}</strong>
                    <div class="muted">${escapeHtml(group.subtitle)}</div>
                </div>
                <div class="cart-group-summary">
                    <span>${escapeHtml(formatMoney(group.items.reduce((sum, item) => sum + Number(item.quantity || 0) * Number(item.unitPrice || 0), 0), order.currencyCode || "USD"))}</span>
                    <span class="cart-group-chip">${group.items.length} line item${group.items.length === 1 ? "" : "s"}</span>
                </div>
            </div>
        `;
        group.items.forEach((item) => {
            const row = document.createElement("article");
            row.className = "order-history-item";
            row.innerHTML = `
                <div class="order-summary-line">
                    <strong>${escapeHtml(item.itemName || item.sku)}</strong>
                    <span class="muted">${escapeHtml(item.quantity)} × ${escapeHtml(formatMoney(item.unitPrice || 0, order.currencyCode || "USD"))}</span>
                </div>
                <div class="fulfillment-inline-meta">
                    <span class="fulfillment-badge ${isPickupItem(item) ? "pickup" : "shipping"}">${escapeHtml(isPickupItem(item) ? "Pickup" : "Shipping")}</span>
                    <span class="muted">${escapeHtml(isPickupItem(item) ? DEMO_PICKUP_LOCATION : "Ships to your saved address")}</span>
                </div>
            `;
            section.appendChild(row);
        });
        itemGroups.appendChild(section);
    });
}

async function initOrderFailed() {
    const message = document.getElementById("failed-message");
    const timeline = document.getElementById("failed-timeline");
    const fulfillmentGrid = document.getElementById("failed-fulfillment-grid");
    const itemGroups = document.getElementById("failed-item-groups");
    const orderNumber = query.get("orderNumber") || state.lastOrderNumber;
    const paymentNumber = query.get("paymentNumber") || state.lastPaymentNumber;
    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
    }
    if (!state.token || !orderNumber) {
        message.textContent = "Sign in to view order details.";
        return;
    }
    try {
        state.order = await api.getOrder(orderNumber);
        if (paymentNumber || state.order.paymentReference) {
            try {
                state.payment = await api.getPayment(paymentNumber || state.order.paymentReference);
            } catch (error) {}
        }
        document.getElementById("failed-order-number").textContent = state.order.orderNumber;
        document.getElementById("failed-order-status").textContent = displayOrderStatus(state.order.status);
        document.getElementById("failed-payment-status").textContent = displayPaymentStatus(state.payment?.paymentStatus || "FAILED");
        document.getElementById("failed-total").textContent = formatMoney(state.order.totalAmount || 0, state.order.currencyCode || "USD");
        message.textContent = state.order.statusReason || state.payment?.failureReason || "This order could not be completed because inventory was no longer available.";
        timeline.innerHTML = "";
        [
            { title: "Order created", detail: formatDate(state.order.createdAt) },
            { title: "Order status", detail: state.order.status || "-" },
            { title: "Failure reason", detail: state.order.statusReason || state.payment?.failureReason || "Inventory unavailable" },
            { title: "Payment reference", detail: state.order.paymentReference || state.payment?.paymentNumber || "Pending" }
        ].forEach((entry) => {
            const node = document.createElement("article");
            node.className = "timeline-item";
            node.innerHTML = `<strong>${escapeHtml(entry.title)}</strong><span>${escapeHtml(entry.detail)}</span>`;
            timeline.appendChild(node);
        });
        renderOrderFulfillmentGroups(fulfillmentGrid, itemGroups, state.order);
    } catch (error) {
        message.textContent = `Unable to load order failure details: ${error.message}`;
    }
}

async function initWishlist() {
    const message = document.getElementById("wishlist-message");
    const meta = document.getElementById("wishlist-meta");
    const grid = document.getElementById("wishlist-grid");
    syncSessionChrome();
    if (!state.token) {
        window.location.href = `/signin.html?returnTo=${encodeURIComponent("/wishlist.html")}`;
        return;
    }
    await resolveSignedInAccount();
    await loadCartSilently();
    updateHomeCartPill();
    const items = getWishlistItems();
    meta.textContent = `${items.length} saved item${items.length === 1 ? "" : "s"}`;
    if (!items.length) {
        message.textContent = "No saved items yet.";
        grid.hidden = true;
        return;
    }
    message.hidden = true;
    grid.hidden = false;
    grid.innerHTML = "";
    items.forEach((item) => grid.appendChild(buildTile(item)));
}

async function initAccount() {
    const message = document.getElementById("account-message");
    const refresh = document.getElementById("account-refresh-button");
    const addAddressButton = document.getElementById("account-add-address-button");
    const addPaymentButton = document.getElementById("account-add-payment-button");
    const membershipButton = document.getElementById("account-membership-button");
    const addressEditor = document.getElementById("account-address-editor");
    const paymentEditor = document.getElementById("account-payment-editor");
    const addresses = document.getElementById("account-addresses");
    const payments = document.getElementById("account-payments");
    const orders = document.getElementById("account-orders");
    syncSessionChrome();
    if (state.token) {
        await resolveSignedInAccount();
        await loadCartSilently();
    }
    updateHomeCartPill();

    async function render() {
        if (!state.token) {
            window.location.href = `/signin.html?returnTo=${encodeURIComponent("/account.html")}`;
            return;
        }
        try {
            const account = state.account || await resolveSignedInAccount();
            if (!account) {
                window.location.href = `/signin.html?returnTo=${encodeURIComponent("/account.html")}`;
                return;
            }
            document.getElementById("account-title").textContent = `${account.fullName || account.username || "Customer"} account`;
            document.getElementById("account-username").textContent = account.username || "-";
            document.getElementById("account-email").textContent = account.email || "-";
            document.getElementById("account-phone").textContent = account.phoneNumber || "-";
            document.getElementById("account-status").textContent = account.status || "-";
            document.getElementById("account-membership").textContent = account.membershipLevel || "-";
            membershipButton.textContent = account.membershipLevel === "PREMIUM" ? "Cancel ShopSmart+" : "Join ShopSmart+";
            addresses.innerHTML = "";
            payments.innerHTML = "";
            const accountPayload = toAccountRequest(account);
            const sortedAddresses = sortAddresses(account.addresses || []);
            const sortedPayments = sortPaymentMethods(account.paymentMethods || []);
            sortedAddresses.forEach((address) => {
                const node = document.createElement("article");
                node.className = "account-card address-card";
                node.innerHTML = `
                    <div class="account-card-header">
                        <div class="account-card-copy">
                            <div class="account-card-label">${escapeHtml(address.label || address.addressType || "Address")}</div>
                            <h3 class="account-card-title">${escapeHtml(address.recipientName || "Saved address")}</h3>
                            <div class="address-cityline">${escapeHtml(formatAddressSummary(address))}</div>
                        </div>
                        ${address.defaultAddress ? `<span class="default-badge">Default</span>` : ""}
                    </div>
                    <div class="address-tag">${escapeHtml(address.addressType || "Address")}</div>
                    <div class="inline-actions">
                        <button class="secondary-button" type="button" data-edit-address="${address.id || ""}">Edit</button>
                        <button class="secondary-button" type="button" data-delete-address="${address.id || ""}">Delete</button>
                    </div>
                `;
                addresses.appendChild(node);
            });
            sortedPayments.forEach((payment) => {
                const node = document.createElement("article");
                node.className = "account-card wallet-card";
                node.innerHTML = `
                    <div class="account-card-header">
                        <div class="account-card-copy">
                            <div class="account-card-label">Payment method</div>
                            <h3 class="account-card-title">${escapeHtml(paymentMethodLabel(payment))}</h3>
                            <div>${escapeHtml(paymentMethodSummary(payment))}</div>
                            <div class="payment-meta">${escapeHtml(paymentMethodMeta(payment))}</div>
                        </div>
                        ${payment.defaultMethod ? `<span class="default-badge">Default</span>` : ""}
                    </div>
                    <div class="payment-chip">${escapeHtml(String(payment.paymentMethodType || "Payment").replaceAll("_", " "))}</div>
                    <div class="inline-actions">
                        <button class="secondary-button" type="button" data-edit-payment="${payment.id || ""}">Edit</button>
                        <button class="secondary-button" type="button" data-delete-payment="${payment.id || ""}">Delete</button>
                    </div>
                `;
                payments.appendChild(node);
            });
            orders.innerHTML = "";
            const orderHistory = await api.getOrdersByCustomer(state.customerId);
            orderHistory
                .sort((left, right) => new Date(right.createdAt || 0) - new Date(left.createdAt || 0))
                .forEach((order) => {
                    const node = document.createElement("article");
                    node.className = "account-card order-card";
                    const fulfillment = fulfillmentSummary(order.items || []);
                    const fulfillmentCards = [
                        fulfillment.shippingItems.length
                            ? `<div class="order-detail-card">
                                    <strong>Shipping</strong>
                                    <div class="order-detail-row">${escapeHtml(formatSnapshotSummary(order.shippingAddress))}</div>
                                    <div class="muted">${fulfillment.shippingUnits} unit${fulfillment.shippingUnits === 1 ? "" : "s"} shipping</div>
                               </div>`
                            : "",
                        fulfillment.pickupItems.length
                            ? `<div class="order-detail-card">
                                    <strong>Pickup</strong>
                                    <div class="order-detail-row">${escapeHtml(DEMO_PICKUP_LOCATION)}</div>
                                    <div class="muted">${fulfillment.pickupUnits} unit${fulfillment.pickupUnits === 1 ? "" : "s"} for pickup</div>
                               </div>`
                            : "",
                        `<div class="order-detail-card">
                            <strong>Billing details</strong>
                            <div class="order-detail-row">${escapeHtml(formatSnapshotSummary(order.billingAddress))}</div>
                        </div>`,
                        order.paymentReference
                            ? `<div class="order-detail-card"><strong>Payment reference</strong><div class="order-detail-row">${escapeHtml(order.paymentReference)}</div></div>`
                            : "",
                        `<div class="order-detail-card">
                            <strong>Fulfillment mix</strong>
                            <div class="order-detail-row">${escapeHtml(fulfillment.shippingItems.length && fulfillment.pickupItems.length ? "Shipping and pickup" : fulfillment.pickupItems.length ? "Pickup only" : "Shipping only")}</div>
                        </div>`
                    ].filter(Boolean).join("");
                    const actionMarkup = canCancelOrder(order)
                        ? `<button class="secondary-button" type="button" data-cancel-order="${escapeHtml(order.orderNumber || "")}">Cancel order</button>`
                        : canRefundOrder(order)
                            ? `<button class="secondary-button" type="button" data-refund-order="${escapeHtml(order.orderNumber || "")}">Request refund</button>`
                            : "";
                    const itemsMarkup = (order.items || []).map((item) => `
                        <div class="order-history-item">
                            <div class="order-summary-line">
                                <strong>${escapeHtml(item.itemName || item.sku)}</strong>
                                <span class="muted">${escapeHtml(item.quantity)} × ${escapeHtml(formatMoney(item.unitPrice || 0, order.currencyCode || "USD"))}</span>
                            </div>
                            <div class="fulfillment-inline-meta">
                                <span class="fulfillment-badge ${isPickupItem(item) ? "pickup" : "shipping"}">${escapeHtml(isPickupItem(item) ? "Pickup" : "Shipping")}</span>
                                <span class="muted">${escapeHtml(isPickupItem(item) ? DEMO_PICKUP_LOCATION : "Ships to your saved address")}</span>
                            </div>
                        </div>
                    `).join("");
                    node.innerHTML = `
                        <button class="order-history-toggle" type="button">
                            <span class="stack-tight order-card-copy">
                                <strong>${escapeHtml(order.orderNumber || "Order")}</strong>
                                <span class="order-meta-inline">${escapeHtml(formatDate(order.createdAt))}</span>
                            </span>
                            <span class="order-amount">${escapeHtml(formatMoney(order.totalAmount || 0, order.currencyCode || "USD"))}</span>
                        </button>
                        <div class="order-history-body" hidden>
                            <div class="order-status-pill">${escapeHtml(displayOrderStatus(order.status))}</div>
                            <div class="order-detail-grid">${fulfillmentCards}</div>
                            <div class="order-items-list">${itemsMarkup || `<div class="muted">No items.</div>`}</div>
                            ${actionMarkup ? `<div class="inline-actions" style="margin-top:12px;">${actionMarkup}</div>` : ""}
                        </div>
                    `;
                    orders.appendChild(node);
                    node.querySelector(".order-history-toggle").addEventListener("click", () => {
                        const body = node.querySelector(".order-history-body");
                        body.hidden = !body.hidden;
                    });
                    const cancelButton = node.querySelector("[data-cancel-order]");
                    if (cancelButton) {
                        cancelButton.addEventListener("click", async () => {
                            await processCancelOrder(order);
                        });
                    }
                    const refundButton = node.querySelector("[data-refund-order]");
                    if (refundButton) {
                        refundButton.addEventListener("click", async () => {
                            await processRefundOrder(order);
                        });
                    }
                });
            if (!(account.addresses || []).length) addresses.innerHTML = `<div class="message-box">No saved addresses for this account.</div>`;
            if (!(account.paymentMethods || []).length) payments.innerHTML = `<div class="message-box">No saved payment methods for this account.</div>`;
            if (!orderHistory.length) orders.innerHTML = `<div class="message-box">No orders yet.</div>`;
            message.textContent = "Your account details are ready.";

            addresses.querySelectorAll("[data-edit-address]").forEach((button, index) => {
                button.addEventListener("click", () => openAddressEditor(sortedAddresses[index]));
            });
            addresses.querySelectorAll("[data-delete-address]").forEach((button, index) => {
                button.addEventListener("click", async () => {
                    const target = sortedAddresses[index];
                    accountPayload.addresses = accountPayload.addresses.filter((item) => item.id !== target.id);
                    await saveAccount(accountPayload);
                });
            });
            payments.querySelectorAll("[data-edit-payment]").forEach((button, index) => {
                button.addEventListener("click", () => openPaymentEditor(sortedPayments[index]));
            });
            payments.querySelectorAll("[data-delete-payment]").forEach((button, index) => {
                button.addEventListener("click", async () => {
                    const target = sortedPayments[index];
                    accountPayload.paymentMethods = accountPayload.paymentMethods.filter((item) => item.id !== target.id);
                    await saveAccount(accountPayload);
                });
            });
        } catch (error) {
            if (String(error.message).includes("401")) {
                clearSession();
                window.location.href = `/signin.html?returnTo=${encodeURIComponent("/account.html")}`;
                return;
            }
            message.textContent = `Unable to load account: ${error.message}`;
        }
    }

    refresh.addEventListener("click", render);
    membershipButton.addEventListener("click", async () => {
        const payload = toAccountRequest(state.account);
        payload.membershipLevel = state.account.membershipLevel === "PREMIUM" ? "REGULAR" : "PREMIUM";
        await saveAccount(payload);
        message.textContent = payload.membershipLevel === "PREMIUM" ? "ShopSmart+ is active. Shipping is free at checkout." : "ShopSmart+ has been canceled.";
    });
    addAddressButton.addEventListener("click", () => openAddressEditor());
    addPaymentButton.addEventListener("click", () => openPaymentEditor());
    document.getElementById("address-cancel-button").addEventListener("click", () => {
        addressEditor.hidden = true;
        state.editingAddressIndex = -1;
    });
    document.getElementById("payment-cancel-button").addEventListener("click", () => {
        paymentEditor.hidden = true;
        state.editingPaymentIndex = -1;
    });
    document.getElementById("address-save-button").addEventListener("click", async () => {
        const validationError = validateAddressEditor();
        if (validationError) {
            message.textContent = validationError;
            return;
        }
        const payload = toAccountRequest(state.account);
        const nextAddress = {
            id: state.account?.addresses?.[state.editingAddressIndex]?.id,
            label: document.getElementById("address-label").value.trim(),
            recipientName: document.getElementById("address-recipient").value.trim(),
            addressLine1: document.getElementById("address-line1").value.trim(),
            addressLine2: document.getElementById("address-line2").value.trim(),
            city: document.getElementById("address-city").value.trim(),
            state: document.getElementById("address-state").value.trim(),
            postalCode: document.getElementById("address-postal").value.trim(),
            country: document.getElementById("address-country").value.trim(),
            addressType: document.getElementById("address-type").value,
            defaultAddress: document.getElementById("address-default").checked
        };
        if (state.editingAddressIndex >= 0) payload.addresses[state.editingAddressIndex] = nextAddress;
        else payload.addresses.push(nextAddress);
        await saveAccount(payload);
        addressEditor.hidden = true;
        state.editingAddressIndex = -1;
    });
    document.getElementById("payment-save-button").addEventListener("click", async () => {
        const validationError = validatePaymentEditor();
        if (validationError) {
            message.textContent = validationError;
            return;
        }
        const payload = toAccountRequest(state.account);
        const nextPayment = {
            id: state.account?.paymentMethods?.[state.editingPaymentIndex]?.id,
            paymentMethodType: document.getElementById("payment-type").value,
            provider: document.getElementById("payment-provider").value.trim(),
            accountToken: document.getElementById("payment-token").value.trim(),
            maskedNumber: document.getElementById("payment-masked").value.trim(),
            cardholderName: document.getElementById("payment-cardholder").value.trim(),
            expiryMonth: Number(document.getElementById("payment-expiry-month").value || 0) || null,
            expiryYear: Number(document.getElementById("payment-expiry-year").value || 0) || null,
            defaultMethod: document.getElementById("payment-default").checked,
            active: true
        };
        if (state.editingPaymentIndex >= 0) payload.paymentMethods[state.editingPaymentIndex] = nextPayment;
        else payload.paymentMethods.push(nextPayment);
        await saveAccount(payload);
        paymentEditor.hidden = true;
        state.editingPaymentIndex = -1;
    });
    await render();

    function openAddressEditor(address = null) {
        state.editingAddressIndex = address ? (state.account.addresses || []).findIndex((item) => item.id === address.id) : -1;
        document.getElementById("address-editor-title").textContent = address ? "Edit address" : "Add address";
        document.getElementById("address-label").value = address?.label || "";
        document.getElementById("address-recipient").value = address?.recipientName || "";
        document.getElementById("address-line1").value = address?.addressLine1 || "";
        document.getElementById("address-line2").value = address?.addressLine2 || "";
        document.getElementById("address-city").value = address?.city || "";
        document.getElementById("address-state").value = address?.state || "";
        document.getElementById("address-postal").value = address?.postalCode || "";
        document.getElementById("address-country").value = address?.country || "US";
        document.getElementById("address-type").value = address?.addressType || "SHIPPING";
        document.getElementById("address-default").checked = Boolean(address?.defaultAddress);
        addressEditor.hidden = false;
    }

    function openPaymentEditor(payment = null) {
        state.editingPaymentIndex = payment ? (state.account.paymentMethods || []).findIndex((item) => item.id === payment.id) : -1;
        document.getElementById("payment-editor-title").textContent = payment ? "Edit payment method" : "Add payment method";
        document.getElementById("payment-type").value = payment?.paymentMethodType || "CREDIT_CARD";
        document.getElementById("payment-provider").value = payment?.provider || "";
        document.getElementById("payment-cardholder").value = payment?.cardholderName || "";
        document.getElementById("payment-masked").value = payment?.maskedNumber || "";
        document.getElementById("payment-token").value = payment?.accountToken || "";
        document.getElementById("payment-expiry-month").value = payment?.expiryMonth || "";
        document.getElementById("payment-expiry-year").value = payment?.expiryYear || "";
        document.getElementById("payment-default").checked = Boolean(payment?.defaultMethod);
        paymentEditor.hidden = false;
    }

    function toAccountRequest(account) {
        return {
            username: account.username,
            fullName: account.fullName,
            email: account.email,
            password: account.password || "",
            phoneNumber: account.phoneNumber,
            membershipLevel: account.membershipLevel,
            addresses: [...(account.addresses || [])],
            paymentMethods: [...(account.paymentMethods || [])]
        };
    }

    async function saveAccount(payload) {
        const updated = await api.updateAccount(state.account.id, payload);
        state.account = updated;
        await render();
    }

    async function processCancelOrder(order) {
        try {
            if (order.status === "PAID") {
                if (!order.paymentReference) {
                    throw new Error("This paid order does not have a payment reference.");
                }
                await api.cancelPaidPayment(order.paymentReference, {
                    idempotencyKey: `cancel-${order.orderNumber}-${Date.now()}`,
                    amount: order.totalAmount,
                    externalReference: `web-cancel-${order.orderNumber}-${Date.now()}`
                });
            } else {
                await api.cancelOrder(order.orderNumber, {
                    cancelRequestId: `cancel-${order.orderNumber}-${Date.now()}`,
                    statusReason: "Canceled by customer"
                });
            }
            message.textContent = `Order ${order.orderNumber} was canceled.`;
            await render();
        } catch (error) {
            message.textContent = `Unable to cancel order: ${error.message}`;
        }
    }

    async function processRefundOrder(order) {
        try {
            if (!order.paymentReference) {
                throw new Error("This order does not have a payment reference.");
            }
            await api.refundPayment(order.paymentReference, {
                idempotencyKey: `refund-${order.orderNumber}-${Date.now()}`,
                amount: order.totalAmount,
                externalReference: `web-refund-${order.orderNumber}-${Date.now()}`
            });
            message.textContent = `Refund requested for ${order.orderNumber}.`;
            await render();
        } catch (error) {
            message.textContent = `Unable to refund order: ${error.message}`;
        }
    }
}

bindGlobalSearch();

switch (page) {
    case "home":
        initHome().catch(console.error);
        break;
    case "signin":
        initSignin();
        break;
    case "signup":
        initSignup();
        break;
    case "product":
        initProduct().catch(console.error);
        break;
    case "cart":
        initCart().catch(console.error);
        break;
    case "checkout":
        initCheckout().catch(console.error);
        break;
    case "confirmation":
        initConfirmation().catch(console.error);
        break;
    case "order-failed":
        initOrderFailed().catch(console.error);
        break;
    case "account":
        initAccount().catch(console.error);
        break;
    case "wishlist":
        initWishlist().catch(console.error);
        break;
    default:
        break;
}
