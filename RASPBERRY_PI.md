# Raspberry Pi Kurulum Rehberi

Bu rehber, **Core Banking API** projesini Raspberry Pi üzerinde çalıştırmak için gereken adımları sırasıyla açıklar.

Proje **Spring Boot 4 + Java 21 + PostgreSQL (TLS)** kullanır.

---

## Ön koşullar

| Gereksinim | Öneri |
|---|---|
| Raspberry Pi | Pi 4 veya Pi 5, **en az 4 GB RAM** |
| İşletim sistemi | **Raspberry Pi OS 64-bit** (Bookworm veya üzeri) |
| Disk | En az 16 GB boş alan |
| Ağ | İnternet (Maven bağımlılıkları için) |

> Pi 3 veya 2 GB RAM'li modellerde Java + PostgreSQL Docker ile çok yavaş kalabilir.

---

## 1. Raspberry Pi'yi hazırlayın

```bash
sudo apt update && sudo apt upgrade -y
sudo reboot
```

SSH ile bağlanıyorsanız:

```bash
ssh pi@<raspberry-pi-ip>
```

---

## 2. Java 21 kurun

```bash
sudo apt install -y openjdk-21-jdk maven git
java -version   # "21" görmelisiniz
```

---

## 3. Docker ve Docker Compose kurun

```bash
curl -fsSL https://get.docker.com | sh
sudo usermod -aG docker $USER
newgrp docker
docker --version
docker compose version
```

---

## 4. Projeyi klonlayın

```bash
cd ~
git clone https://github.com/AzaSat231/core-banking-system.git
cd core-banking-system
```

Kendi makinenizdeki kopyayı kullanacaksanız `scp` veya `git push` ile Pi'ye aktarın.

---

## 5. PostgreSQL TLS sertifikalarını oluşturun

`docker-compose.yml` PostgreSQL'i **TLS zorunlu** başlatır. Sertifikalar olmadan container ayağa kalkmaz.

Proje `~/atm-tls/postgres/` altında şu dosyaları bekler:

- `ca.pem`
- `server.crt`
- `server.key`

Sertifika scripti (`graduation_project/scripts/gen_postgres_server_cert.sh`) ayrı bir repoda olabilir; elle de oluşturabilirsiniz:

```bash
mkdir -p ~/atm-tls/postgres
cd ~/atm-tls/postgres

# CA
openssl genrsa -out ca.key 4096
openssl req -x509 -new -nodes -key ca.key -sha256 -days 3650 \
  -subj "/CN=ATM Postgres CA" -out ca.pem

# Sunucu sertifikası
openssl genrsa -out server.key 2048
openssl req -new -key server.key \
  -subj "/CN=localhost" -out server.csr
openssl x509 -req -in server.csr -CA ca.pem -CAkey ca.key -CAcreateserial \
  -out server.crt -days 3650 -sha256

chmod 600 server.key ca.key
```

---

## 6. PostgreSQL'i Docker ile başlatın

```bash
cd ~/core-banking-system
docker compose up -d
```

Kontrol:

```bash
docker compose ps
docker logs postgres-spring-boot
```

Beklenen portlar:

| Port | Servis | Veritabanı |
|---|---|---|
| **5332** | `postgres-spring-boot` | `dbv1` (core banking) |
| **5433** | `postgres-middleware` | `mwdb` (middleware) |

---

## 7. Ortam değişkenlerini ayarlayın (isteğe bağlı)

Proje kökünde `.env` dosyası kullanılabilir:

```bash
cd ~/core-banking-system
nano .env
```

Örnek içerik:

```bash
export MIDDLEWARE_SERVICE_TOKEN=guclu-bir-token-buraya
export SMTP_HOST=smtp.gmail.com
export SMTP_PORT=587
export SMTP_USERNAME=your-email@example.com
export SMTP_PASSWORD=your-app-password
export SMTP_AUTH=true
export SMTP_STARTTLS=true
export CARD_EMAIL_FROM=no-reply@yourbank.com
export CARD_EMAIL_BANK_NAME="Core Banking"
export CARD_EMAIL_ATM_URL=https://atm.local
```

SMTP boş bırakılırsa uygulama çalışır; kart e-postası gönderilmez.

---

## 8. Spring Boot uygulamasını çalıştırın

### Yöntem A — Maven ile (geliştirme / test)

```bash
cd ~/core-banking-system
source .env 2>/dev/null || true
./mvnw spring-boot:run
```

İlk derleme Pi'de **10–30 dakika** sürebilir.

### Yöntem B — JAR ile (daha pratik)

```bash
cd ~/core-banking-system
./mvnw clean package -DskipTests
java -jar target/core-banking-*.jar
```

### Yöntem C — Docker ile uygulama

```bash
cd ~/core-banking-system
docker build -t core-banking .
docker run -d --name core-banking \
  --network host \
  -e DATABASE_URL="jdbc:postgresql://localhost:5332/dbv1?sslmode=verify-full&sslrootcert=/certs/ca.pem" \
  -e DATABASE_USERNAME=dbv1 \
  -e DATABASE_PASSWORD=pass123 \
  -e JWT_SECRET="ThisIsAVeryLongSecretKeyForCoreBankingAppThatIsAtLeast256Bits!" \
  -e MIDDLEWARE_SERVICE_TOKEN="guclu-bir-token" \
  -v ~/atm-tls/postgres/ca.pem:/certs/ca.pem:ro \
  core-banking
```

---

## 9. Çalıştığını doğrulayın

Uygulama varsayılan olarak **sadece localhost**'a bağlanır (`server.address=127.0.0.1`):

```bash
curl http://127.0.0.1:8080/swagger-ui.html
```

Kayıt ve giriş:

```bash
curl -X POST http://127.0.0.1:8080/auth/register \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin123","role":"ROLE_ADMIN"}'

curl -X POST http://127.0.0.1:8080/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin1","password":"admin123"}'
```

Swagger: `http://127.0.0.1:8080/swagger-ui.html`

Dış ağdan erişim için `server.address` değiştirilmeli veya reverse proxy (Caddy) kullanılmalıdır.

---

## 10. Kalıcı servis (systemd) — isteğe bağlı

```bash
sudo nano /etc/systemd/system/core-banking.service
```

```ini
[Unit]
Description=Core Banking API
After=docker.service
Requires=docker.service

[Service]
User=pi
WorkingDirectory=/home/pi/core-banking-system
EnvironmentFile=/home/pi/core-banking-system/.env
ExecStart=/usr/bin/java -jar /home/pi/core-banking-system/target/core-banking-0.0.1-SNAPSHOT.jar
Restart=on-failure

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now core-banking
sudo systemctl status core-banking
```

---

## Tam ATM kurulumu için ek adımlar

Bu repo yalnızca **core banking API**'dir. Tam ATM sistemi için ayrıca gerekir:

1. **Middleware** servisi (`MIDDLEWARE_URL`, `CORE_BANKING_URL`)
2. **Caddy** ile mTLS (`api.local`, `mw.local`, `atm.local`)
3. `/etc/hosts` veya DNS kayıtları
4. Parmak izi / ATM donanımı entegrasyonu

Uygulama şu an middleware'in Caddy üzerinden `127.0.0.1:8080`'e erişmesi için tasarlanmış; doğrudan dış ağa açmak yerine reverse proxy + TLS kullanın.

---

## Sık karşılaşılan sorunlar

| Sorun | Çözüm |
|---|---|
| `Missing server.crt or server.key` | [Adım 5](#5-postgresql-tls-sertifikalarını-oluşturun)'teki TLS sertifikalarını oluşturun |
| PostgreSQL bağlantı hatası | `docker compose ps` ile container'ın ayakta olduğunu kontrol edin |
| `sslmode=verify-full` hatası | `ca.pem` yolunun doğru olduğundan emin olun (`~/atm-tls/postgres/ca.pem`) |
| Out of memory | Swap ekleyin veya sadece JAR çalıştırın (Maven + Docker birlikte ağır) |
| Dışarıdan erişilemiyor | `application.properties` içinde `server.address=127.0.0.1` var; Caddy veya `0.0.0.0` gerekir |

---

## Özet akış

```
Raspberry Pi OS 64-bit
        ↓
Java 21 + Docker kur
        ↓
Projeyi klonla
        ↓
TLS sertifikaları oluştur
        ↓
docker compose up -d
        ↓
mvnw spring-boot:run veya JAR
        ↓
localhost:8080 test
        ↓
Middleware + Caddy (tam ATM için)
```

---

## İlgili dosyalar

| Dosya | Açıklama |
|---|---|
| `docker-compose.yml` | PostgreSQL container tanımı (TLS zorunlu) |
| `Dockerfile` | Uygulama container imajı |
| `src/main/resources/application.properties` | Yerel geliştirme ayarları |
| `src/main/resources/application-prod.properties` | Prod ortam değişkenleri |
| `postgres/pg_hba.conf` | PostgreSQL TLS erişim kuralları |
