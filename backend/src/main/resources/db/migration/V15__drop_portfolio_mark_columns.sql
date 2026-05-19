ALTER TABLE portfolio_position
    DROP COLUMN IF EXISTS last_mark_price,
    DROP COLUMN IF EXISTS unrealized_pnl;
