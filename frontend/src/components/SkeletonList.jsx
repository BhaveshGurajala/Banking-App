import './SkeletonList.css';

function SkeletonList({ rows = 3 }) {
  return (
    <div className="card">
      {Array.from({ length: rows }).map((_, i) => (
        <div key={i} className="skeleton-row">
          <div>
            <div className="skeleton-block" style={{ width: '120px', height: '14px', marginBottom: '8px' }} />
            <div className="skeleton-block" style={{ width: '80px', height: '11px' }} />
          </div>
          <div className="skeleton-block" style={{ width: '60px', height: '16px' }} />
        </div>
      ))}
    </div>
  );
}

export default SkeletonList;