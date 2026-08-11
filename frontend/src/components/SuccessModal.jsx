import './SuccessModal.css';

function SuccessModal({ title, message, onClose }) {
  return (
    <div className="modal-overlay">
      <div className="modal-card">
        <div className="modal-icon">✓</div>
        <h2>{title}</h2>
        <p className="text-muted modal-message">{message}</p>
        <button className="button" onClick={onClose}>Okay</button>
      </div>
    </div>
  );
}

export default SuccessModal;