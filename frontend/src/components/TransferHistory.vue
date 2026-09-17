<template>
    <div class="card shadow-sm">
        <div class="card-header bg-white">
            <div class="d-flex justify-content-between align-items-center">
                <h2 class="h5 mb-0">
                    <i class="bi bi-clock-history me-2"></i>
                    Historial de transferencias
                </h2>

                <button
                    type="button"
                    class="btn btn-outline-secondary btn-sm"
                    :disabled="loading"
                    @click="loadTransfers"
                >
                    <span
                        v-if="loading"
                        class="spinner-border spinner-border-sm me-1"
                        role="status"
                        aria-hidden="true"
                    ></span>

                    <i
                        v-else
                        class="bi bi-arrow-clockwise me-1"
                    ></i>

                    Actualizar
                </button>
            </div>
        </div>

        <div class="card-body p-0">
            <div
                v-if="errorMessage"
                class="alert alert-danger rounded-0 mb-0"
                role="alert"
            >
                <i class="bi bi-exclamation-triangle me-2"></i>
                {{ errorMessage }}
            </div>

            <div
                v-if="loading && transfers.length === 0"
                class="text-center py-5"
            >
                <div
                    class="spinner-border text-primary mb-3"
                    role="status"
                ></div>

                <p class="text-muted mb-0">
                    Cargando transferencias...
                </p>
            </div>

            <div
                v-else-if="!loading && transfers.length === 0 && !errorMessage"
                class="text-center py-5"
            >
                <i class="bi bi-inbox fs-1 text-muted"></i>

                <h3 class="h6 mt-3">
                    No hay transferencias
                </h3>

                <p class="text-muted mb-0">
                    Todavía no se registraron transferencias.
                </p>
            </div>

            <div
                v-else
                class="table-responsive"
            >
                <table class="table table-hover align-middle mb-0">
                    <thead class="table-light">
                        <tr>
                            <th>ID</th>
                            <th>Origen</th>
                            <th>Destino</th>
                            <th class="text-end">Monto</th>
                            <th>Concepto</th>
                            <th>Estado</th>
                            <th>Fecha</th>
                        </tr>
                    </thead>

                    <tbody>
                        <tr
                            v-for="transfer in transfers"
                            :key="transfer.id"
                        >
                            <td>
                                <span class="fw-semibold">
                                    #{{ transfer.id }}
                                </span>
                            </td>

                            <td>
                                {{ transfer.sourceAccount }}
                            </td>

                            <td>
                                {{ transfer.destinationAccount }}
                            </td>

                            <td class="text-end fw-semibold">
                                {{ formatAmount(transfer.amount) }}
                            </td>

                            <td>
                                {{ transfer.description }}
                            </td>

                            <td>
                                <span
                                    class="badge"
                                    :class="getStatusClass(transfer.status)"
                                >
                                    <i
                                        class="bi me-1"
                                        :class="getStatusIcon(transfer.status)"
                                    ></i>

                                    {{ transfer.status }}
                                </span>
                            </td>

                            <td>
                                {{ formatDate(transfer.createdAt) }}
                            </td>
                        </tr>
                    </tbody>
                </table>
            </div>
        </div>

        <div
            v-if="transfers.length > 0"
            class="card-footer bg-white text-muted small"
        >
            Mostrando las últimas {{ transfers.length }} transferencias.
        </div>
    </div>
</template>

<script>
import { getTransfers } from '../services/transferService';

export default {
    name: 'TransferHistory',

    data() {
        return {
            transfers: [],
            loading: false,
            errorMessage: ''
        };
    },

    mounted() {
        this.loadTransfers();
    },

    methods: {
        async loadTransfers() {
            this.loading = true;
            this.errorMessage = '';

            try {
                const response = await getTransfers();

                this.transfers = response.data;
            } catch (error) {
                this.errorMessage = this.getErrorMessage(error);
            } finally {
                this.loading = false;
            }
        },

        getStatusClass(status) {
            const classes = {
                EXITOSA: 'bg-success',
                RECHAZADA: 'bg-danger',
                PENDIENTE: 'bg-warning text-dark'
            };

            return classes[status] || 'bg-secondary';
        },

        getStatusIcon(status) {
            const icons = {
                EXITOSA: 'bi-check-circle',
                RECHAZADA: 'bi-x-circle',
                PENDIENTE: 'bi-clock'
            };

            return icons[status] || 'bi-question-circle';
        },

        formatAmount(amount) {
            return new Intl.NumberFormat('es-PY', {
                minimumFractionDigits: 2,
                maximumFractionDigits: 2
            }).format(amount);
        },

        formatDate(date) {
            if (!date) {
                return '-';
            }

            return new Intl.DateTimeFormat('es-PY', {
                dateStyle: 'short',
                timeStyle: 'short'
            }).format(new Date(date));
        },

        getErrorMessage(error) {
            if (error.response?.data?.message) {
                return error.response.data.message;
            }

            if (error.message === 'Network Error') {
                return 'No se pudo conectar con el servidor.';
            }

            return 'No se pudo cargar el historial de transferencias.';
        }
    }
};
</script>