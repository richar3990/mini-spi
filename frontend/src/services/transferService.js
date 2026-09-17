import axios from 'axios';

const apiClient = axios.create({
    baseURL: `${import.meta.env.VITE_API_URL}/api/v1`,
    headers: {
        'Content-Type': 'application/json'
    }
});

export function createTransfer(transfer, idempotencyKey) {
    return apiClient.post(
        '/transferencias',
        transfer,
        {
            headers: {
                'X-Idempotency-Key': idempotencyKey
            }
        }
    );
}

export function getTransfers() {
    return apiClient.get('/transferencias');
}