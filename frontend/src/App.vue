<template>
    <div class="min-vh-100 bg-light">
        <header class="bg-dark text-white py-3 shadow-sm">
            <div class="container">
                <div class="d-flex align-items-center">
                    <i class="bi bi-bank2 fs-3 me-2"></i>
                    <div>
                        <h1 class="h4 mb-0">Mini SPI</h1>
                        <small class="text-white-50">
                            Simulador de Transferencias Inmediatas
                        </small>
                    </div>
                </div>
            </div>
        </header>

        <main class="container py-4">
            <div class="row g-4">
                <div class="col-12">
                    <TransferForm
                        @transfer-created="refreshHistory"
                    />
                </div>

                <div class="col-12">
                    <TransferHistory ref="transferHistory" />
                </div>
            </div>
        </main>
    </div>
</template>

<script>
import TransferForm from './components/TransferForm.vue';
import TransferHistory from './components/TransferHistory.vue';

export default {
    name: 'App',

    components: {
        TransferForm,
        TransferHistory
    },

    methods: {
        refreshHistory() {
            this.$refs.transferHistory.loadTransfers();
        }
    }
};
</script>