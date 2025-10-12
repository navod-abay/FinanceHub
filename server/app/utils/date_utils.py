from datetime import datetime, timezone
from typing import Tuple


def get_date_components(date_obj: datetime) -> Tuple[int, int, int]:
    """
    Convert a datetime object to (day, month, year) tuple
    Similar to the Android util function
    """
    return date_obj.day, date_obj.month, date_obj.year


def unix_timestamp_to_datetime(timestamp: int) -> datetime:
    """Convert Unix timestamp to datetime object"""
    return datetime.fromtimestamp(timestamp, tz=timezone.utc)


def datetime_to_unix_timestamp(dt: datetime) -> int:
    """Convert datetime object to Unix timestamp"""
    return int(dt.timestamp())


def get_current_timestamp() -> int:
    """Get current Unix timestamp"""
    return int(datetime.now(timezone.utc).timestamp())


def is_same_month_year(date1: datetime, date2: datetime) -> bool:
    """Check if two dates are in the same month and year"""
    return date1.month == date2.month and date1.year == date2.year