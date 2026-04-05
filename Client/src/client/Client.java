public class Client {

    private String firstName;
    private String lastName;
    private String CNI;
    private String phoneNumber;

    public Client() {
    }

    public Client(String firstName, String lastName, String CNI, String phoneNumber) {
        this.firstName = firstName;
        this.lastName = lastName;
        this.CNI = CNI;
        this.phoneNumber = phoneNumber;
    }

    public String getFirstName() {
        return firstName;
    }

    public void setFirstName(String firstName) {
        this.firstName = firstName;
    }

    public String getLastName() {
        return lastName;
    }

    public void setLastName(String lastName) {
        this.lastName = lastName;
    }

    public String getCNI() {
        return CNI;
    }

    public void setCNI(String CNI) {
        this.CNI = CNI;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}
