# Glosario del Dominio (Lenguaje Ubicuo)

Términos usados en specs, código y documentación. Fuente de verdad para la
nomenclatura de entidades, VOs y casos de uso.

| Término | Definición |
| --- | --- |
| **Cliente** | Persona titular de una o más cuentas. |
| **Cuenta** | Agregado bancario con `CBU`, tipo, saldo y estado. |
| **Movimiento** | Registro de una operación sobre una cuenta (`DEPOSITO`, `RETIRO`, `TRANSFERENCIA_ENTRANTE`, `TRANSFERENCIA_SALIENTE`). |
| **Saldo** | Monto actual de una cuenta, siempre `>= 0`. |
| **CBU** | Número único que identifica una cuenta (value object). |
| **DNI** | Documento único del cliente (value object). |
| **Money** | Value object inmutable que combina `BigDecimal` y moneda. |
| **Depósito** | Ingreso de dinero a una cuenta. |
| **Retiro** | Egreso de dinero de una cuenta. |
| **Transferencia** | Operación atómica que debita origen y acredita destino. |
| **Tipo de cuenta** | `CAJA_AHORRO` o `CUENTA_CORRIENTE`. |
| **Estado de cuenta** | `ACTIVA` o `BLOQUEADA`. |
| **Rol** | `CLIENTE` (opera sus cuentas) o `ADMIN` (gestión y caja). |
| **Límite diario** | Monto máximo de transferencias por cliente por día. |

## Reglas de negocio transversales

- El saldo nunca es negativo.
- Una transferencia es atómica (dos movimientos en una transacción).
- Un `CLIENTE` solo opera sus propias cuentas.
- Las contraseñas se guardan con BCrypt.
