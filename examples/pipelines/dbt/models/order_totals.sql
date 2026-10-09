select tenant, count(*) as n, sum(amount) as total
from {{ source('sales', 'orders') }}
group by tenant
