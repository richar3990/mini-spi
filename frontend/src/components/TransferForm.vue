<template>
  <div class="card shadow-sm transfer-card">
    <div v-if="loading" class="transfer-loading">
      <div class="text-center">
        <div
          class="spinner-border text-primary mb-3"
          role="status"
          aria-hidden="true"
        ></div>

        <div class="fw-semibold">Procesando transferencia...</div>

        <small class="text-muted"> Por favor, espere un momento. </small>
      </div>
    </div>
    <div class="card-header bg-white">
      <h2 class="h5 mb-0">
        <i class="bi bi-send me-2"></i>
        Nueva transferencia
      </h2>
    </div>

    <div class="card-body">
      <div v-if="successMessage" class="alert alert-success" role="alert">
        <i class="bi bi-check-circle me-2"></i>
        {{ successMessage }}
      </div>

      <div v-if="errorMessage" class="alert alert-danger" role="alert">
        <i class="bi bi-exclamation-triangle me-2"></i>
        {{ errorMessage }}
      </div>

      <form @submit.prevent="submitTransfer" autocomplete="off">
        <div class="row g-3">
          <div class="col-md-6">
            <label for="sourceAccount" class="form-label"> Cuenta origen </label>

            <input
              id="sourceAccount"
              v-model.trim="form.sourceAccount"
              type="text"
              class="form-control"
              placeholder="Ej. 10000001"
              maxlength="30"
              :disabled="loading"
              
            />
          </div>

          <div class="col-md-6">
            <label for="destinationAccount" class="form-label"> Cuenta destino </label>

            <input
              id="destinationAccount"
              v-model.trim="form.destinationAccount"
              type="text"
              class="form-control"
              placeholder="Ej. 10000002"
              maxlength="30"
              :disabled="loading"
            />
          </div>

          <div class="col-md-6">
            <label for="amount" class="form-label"> Monto </label>

            <div class="input-group">
              <span class="input-group-text">₲</span>

              <input
                id="amount"
                v-model="form.amount"
                type="number"
                class="form-control"
                placeholder="0.00"
                min="0.01"
                step="0.01"
                :disabled="loading"
              />
            </div>
          </div>

          <div class="col-md-6">
            <label for="description" class="form-label"> Concepto </label>

            <input
              id="description"
              v-model.trim="form.description"
              type="text"
              class="form-control"
              placeholder="Ej. Pago de servicios"
              maxlength="255"
              :disabled="loading"
            />
          </div>

          <div class="col-12 d-flex justify-content-end">
            <button type="submit" class="btn btn-primary" :disabled="loading">
              <span
                v-if="loading"
                class="spinner-border spinner-border-sm me-2"
                role="status"
                aria-hidden="true"
              ></span>

              <i v-else class="bi bi-send me-2"></i>

              {{ loading ? "Procesando..." : "Transferir" }}
            </button>
          </div>
        </div>
      </form>
    </div>
  </div>
</template>

<script>
import { createTransfer } from "../services/transferService";

export default {
  name: "TransferForm",

  emits: ["transfer-created"],

  data() {
    return {
      form: {
        sourceAccount: "",
        destinationAccount: "",
        amount: "",
        description: "",
      },

      loading: false,
      successMessage: "",
      errorMessage: "",
      timeoutId: null,
    };
  },

  methods: {
    showMessage(type, text, duration = 5000) {
      if (this.timeoutId) {
        clearTimeout(this.timeoutId);
      }

      if (type === "success") {
        this.successMessage = text;
        this.errorMessage = "";
      } else {
        this.errorMessage = text;
        this.successMessage = "";
      }
      this.timeoutId = setTimeout(() => {
        this.clearMessages();
      }, duration);
    },

    clearMessages() {
      this.successMessage = "";
      this.errorMessage = "";
      if (this.timeoutId) {
        clearTimeout(this.timeoutId);
        this.timeoutId = null;
      }
    },

    async submitTransfer() {
      this.clearMessages();
      if (!this.validateForm()) {
        return;
      }
      this.loading = true;

      const idempotencyKey = crypto.randomUUID();

      const transfer = {
        sourceAccount: this.form.sourceAccount,
        destinationAccount: this.form.destinationAccount,
        amount: Number(this.form.amount),
        description: this.form.description,
      };

      try {
        const response = await createTransfer(transfer, idempotencyKey);

        const transferResponse = response.data;

        if (transferResponse.status === "EXITOSA") {
          this.showMessage("success", "La transferencia fue procesada correctamente.");
        } else if (transferResponse.status === "RECHAZADA") {
          this.showMessage("error", "La transferencia fue rechazada.");
        } else {
          this.showMessage(
            "success",
            "La transferencia quedó pendiente de procesamiento."
          );
        }

        this.resetForm();

        this.$emit("transfer-created", transferResponse);
      } catch (error) {
        this.showMessage("error", this.getErrorMessage(error));
      } finally {
        this.loading = false;
      }
    },

    validateForm() {
      if (!this.form.sourceAccount) {
        this.showMessage("error", "La cuenta origen es obligatoria.");
        return false;
      }

      if (!this.form.destinationAccount) {
        this.showMessage("error", "La cuenta destino es obligatoria.");
        return false;
      }

      if (this.form.sourceAccount === this.form.destinationAccount) {
        this.showMessage("error", "La cuenta origen y destino deben ser diferentes.");
        return false;
      }

      if (!this.form.amount || Number(this.form.amount) <= 0) {
        this.showMessage("error", "El monto debe ser mayor que cero.");
        return false;
      }

      if (!this.form.description) {
        this.showMessage("error", "El concepto es obligatorio.");
        return false;
      }

      return true;
    },

    getErrorMessage(error) {
      if (error.response?.data?.message) {
        return error.response.data.message;
      }

      if (error.response?.data?.errors?.length) {
        return error.response.data.errors.join(", ");
      }

      if (error.message === "Network Error") {
        return "No se pudo conectar con el servidor.";
      }

      return "Ocurrió un error al procesar la transferencia.";
    },

    resetForm() {
      this.form = {
        sourceAccount: "",
        destinationAccount: "",
        amount: "",
        description: "",
      };
    },
  },

  // Limpia el temporizador si el usuario sale del componente
  beforeUnmount() {
    if (this.timeoutId) {
      clearTimeout(this.timeoutId);
    }
  },
};
</script>
