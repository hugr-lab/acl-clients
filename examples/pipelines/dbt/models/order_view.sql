{{ config(materialized='view') }}
select tenant, total from {{ ref('order_totals') }}
